package com.authord.mkdocs.runtime

import java.nio.file.Path

/** API version for runtime command-runner seam. */
const val COMMAND_RUNNER_API_VERSION: String = "1.0.0"

/**
 * Command runner contract used by runtime bootstrap orchestration.
 *
 * API Version: [COMMAND_RUNNER_API_VERSION]
 */
fun interface CommandRunner {
    /**
     * Runs a process command in the given working directory path.
     *
     * @param command tokenized command list.
     * @param workingDir absolute or project-relative working directory string.
     * @return process execution details.
     */
    fun run(command: List<String>, workingDir: String): CommandResult
}

/**
 * Process command execution result envelope.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
)

/**
 * Runtime bootstrap result envelope.
 */
data class BootstrapResult(
    val success: Boolean,
    val runtimePath: String,
    val executedCommands: List<List<String>>,
    val skipped: Boolean,
    val errorMessage: String = "",
)

/**
 * Bootstraps a plugin-managed runtime using `uv` and installs `mkdocs`.
 *
 * Usage:
 * - Call once per activation attempt.
 * - Repeated calls for same project path skip duplicate setup.
 */
class UvBootstrapService(
    private val commandRunner: CommandRunner,
) {
    private val bootstrappedProjects = mutableSetOf<String>()

    /**
     * Ensures project runtime exists and required packages are installed.
     *
     * @param projectPath root project path.
     * @return bootstrap status, executed commands, and error details on failure.
     */
    fun bootstrap(projectPath: String): BootstrapResult {
        val runtimePath = Path.of(projectPath).resolve(".mkdocs-plugin-venv").toString()

        if (bootstrappedProjects.contains(projectPath)) {
            return BootstrapResult(
                success = true,
                runtimePath = runtimePath,
                executedCommands = emptyList(),
                skipped = true,
            )
        }

        val setupCommand = listOf("uv", "venv", runtimePath)
        val installCommand = listOf("uv", "pip", "install", "mkdocs")
        val executed = mutableListOf<List<String>>()

        val setupResult = commandRunner.run(setupCommand, projectPath)
        executed += setupCommand
        if (setupResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                executedCommands = executed,
                skipped = false,
                errorMessage = setupResult.stderr.ifBlank { "Failed to create runtime" },
            )
        }

        val installResult = commandRunner.run(installCommand, projectPath)
        executed += installCommand
        if (installResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                executedCommands = executed,
                skipped = false,
                errorMessage = installResult.stderr.ifBlank { "Failed to install mkdocs" },
            )
        }

        bootstrappedProjects += projectPath
        return BootstrapResult(
            success = true,
            runtimePath = runtimePath,
            executedCommands = executed,
            skipped = false,
        )
    }
}
