package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.RuntimeServerConfig
import com.authord.mkdocs.runtime.UvBootstrapService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists

/**
 * Result returned by plugin activation flow.
 */
data class ActivationResult(
    val success: Boolean,
    val reason: ActivationFailureReason? = null,
    val message: String = "",
    val previewUrl: String = "",
)

/**
 * Orchestrates first activation bootstrap, runtime startup, and preview opening.
 */
class PluginActivationService(
    private val bootstrapService: UvBootstrapService,
    private val processManager: MkdocsProcessManager,
    private val baseUrlDetector: BaseUrlDetector,
    private val previewPaneCoordinator: PreviewPaneCoordinator,
    private val errorPresenter: ActivationErrorPresenter,
) {
    /**
     * Activates plugin runtime for a project.
     *
     * @param projectId stable project key.
     * @param projectPath project root path.
     * @param startupOutput runtime startup stdout used for base URL detection.
     * @param featureFlags effective feature-flag policy.
     */
    fun activate(
        projectId: String,
        projectPath: String,
        startupOutput: String,
        featureFlags: FeatureFlagPolicy,
    ): ActivationResult {
        if (!featureFlags.allowsMvpFlow() || !featureFlags.disallowsFutureCycleFeatures()) {
            val reason = ActivationFailureReason.MVP_DISABLED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        val bootstrapResult = bootstrapService.bootstrap(projectPath)
        if (!bootstrapResult.success) {
            val reason = ActivationFailureReason.BOOTSTRAP_FAILED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, bootstrapResult.errorMessage),
            )
        }

        val startResult = processManager.start(
            projectId = projectId,
            workingDir = projectPath,
            config = RuntimeServerConfig(
                command = parentBoundServeCommand(
                    projectPath = projectPath,
                    runtimePath = bootstrapResult.runtimePath,
                    uvExecutablePath = bootstrapResult.uvExecutablePath,
                ),
            ),
        )
        if (!startResult.started) {
            val reason = ActivationFailureReason.START_FAILED
            val failureDetails = startFailureDetails(
                projectPath = projectPath,
                startupOutput = startResult.startupOutput,
            )
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, failureDetails),
            )
        }

        val startupOutputCandidates = listOf(startupOutput, startResult.startupOutput)
            .filter { it.isNotBlank() }
        val resolvedStartupOutput = startupOutputCandidates.joinToString("\n")
        val baseUrl = baseUrlDetector.detectBaseUrl(resolvedStartupOutput)
        if (baseUrl == null) {
            // Keep action/tool-window start paths re-invokable when URL detection fails.
            // Without this cleanup the process remains marked as running and the start action is disabled.
            processManager.stop(projectId)
            val reason = ActivationFailureReason.BASE_URL_NOT_FOUND
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        previewPaneCoordinator.open(projectId, baseUrl)
        return ActivationResult(
            success = true,
            previewUrl = baseUrl,
            message = "Activation completed",
        )
    }

    private fun startFailureDetails(projectPath: String, startupOutput: String): String {
        val normalizedOutput = startupOutput.trim()
        if (normalizedOutput.isNotBlank()) {
            return normalizedOutput
        }

        val rootPath = Path.of(projectPath)
        val hasMkdocsConfig = rootPath.resolve("mkdocs.yml").exists() || rootPath.resolve("mkdocs.yaml").exists()
        if (!hasMkdocsConfig) {
            return "No mkdocs.yml or mkdocs.yaml found in project root: $projectPath"
        }

        return ""
    }

    private fun parentBoundServeCommand(
        projectPath: String,
        runtimePath: String,
        uvExecutablePath: String,
    ): List<String> {
        val scriptPath = ensureParentGuardScript(projectPath)
        val parentPid = ProcessHandle.current().pid().toString()

        return listOf(
            uvExecutablePath,
            "run",
            "--python",
            runtimePath,
            "python",
            scriptPath.toString(),
            "--parent-pid",
            parentPid,
            "--working-dir",
            projectPath,
            "--",
            "mkdocs",
            "serve",
            "--livereload",
            "--dirty",
        )
    }

    private fun ensureParentGuardScript(projectPath: String): Path {
        val runtimeDir = Path.of(projectPath).resolve(".mkdocs-plugin-runtime")
        val scriptPath = runtimeDir.resolve("serve_with_parent_guard.py")
        val script = """
            import argparse
            import errno
            import os
            import subprocess
            import sys
            import threading
            import time
            
            def parent_alive(parent_pid: int) -> bool:
                if parent_pid <= 0:
                    return False
                try:
                    os.kill(parent_pid, 0)
                    return True
                except OSError as exc:
                    if exc.errno in (errno.ESRCH, errno.EINVAL):
                        return False
                    if exc.errno == errno.EPERM:
                        return True
                    return False
            
            def terminate_process(proc: subprocess.Popen[str]) -> None:
                if proc.poll() is not None:
                    return
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
            
            def stream_output(proc: subprocess.Popen[str]) -> None:
                assert proc.stdout is not None
                for line in proc.stdout:
                    sys.stdout.write(line)
                    sys.stdout.flush()
            
            def main() -> int:
                parser = argparse.ArgumentParser()
                parser.add_argument("--parent-pid", type=int, required=True)
                parser.add_argument("--working-dir", required=True)
                parser.add_argument("command", nargs=argparse.REMAINDER)
                args = parser.parse_args()
            
                command = args.command
                if command and command[0] == "--":
                    command = command[1:]
                if not command:
                    print("No command supplied for guarded execution.", file=sys.stderr)
                    return 2
            
                process = subprocess.Popen(
                    command,
                    cwd=args.working_dir,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )
            
                reader = threading.Thread(target=stream_output, args=(process,), daemon=True)
                reader.start()
            
                while True:
                    exit_code = process.poll()
                    if exit_code is not None:
                        return exit_code
                    if not parent_alive(args.parent_pid):
                        terminate_process(process)
                        return 0
                    time.sleep(0.5)
            
            if __name__ == "__main__":
                sys.exit(main())
        """.trimIndent() + "\n"

        Files.createDirectories(runtimeDir)
        Files.writeString(
            scriptPath,
            script,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return scriptPath
    }
}
