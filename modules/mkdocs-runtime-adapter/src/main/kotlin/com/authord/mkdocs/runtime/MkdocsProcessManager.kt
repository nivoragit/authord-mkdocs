package com.authord.mkdocs.runtime

/** API version for runtime process lifecycle seams. */
const val PROCESS_LIFECYCLE_API_VERSION: String = "1.0.0"

/**
 * Runtime start configuration.
 *
 * Contract:
 * - `mkdocs serve` defaults are used by default.
 * - Optional `extraArgs` may be supplied by callers for controlled extensions.
 */
data class RuntimeServerConfig(
    val command: List<String> = listOf("mkdocs", "serve"),
    val extraArgs: List<String> = emptyList(),
)

/**
 * Handle for a spawned runtime process.
 *
 * API Version: [PROCESS_LIFECYCLE_API_VERSION]
 */
interface ManagedProcessHandle {
    val id: String

    /** Stops the underlying process. */
    fun stop()

    /** Returns `true` when process is alive. */
    fun isAlive(): Boolean

    /**
     * Returns startup output observed for this process, if available.
     *
     * Implementations that do not capture output may return an empty string.
     */
    fun startupOutput(): String = ""
}

/**
 * Launches process commands for runtime management.
 *
 * API Version: [PROCESS_LIFECYCLE_API_VERSION]
 */
fun interface ProcessLauncher {
    /**
     * Launches a process command in a working directory.
     */
    fun launch(command: List<String>, workingDir: String): ManagedProcessHandle
}

/**
 * Result returned from runtime start/restart operations.
 */
data class RuntimeStartResult(
    val started: Boolean,
    val processId: String,
    val command: List<String>,
    val alreadyRunning: Boolean,
    val startupOutput: String = "",
)

private data class RunningProcess(
    val handle: ManagedProcessHandle,
    val workingDir: String,
    val config: RuntimeServerConfig,
    val command: List<String>,
)

/**
 * Manages MkDocs runtime lifecycle with single-instance semantics per project.
 */
class MkdocsProcessManager(
    private val processLauncher: ProcessLauncher,
) {
    private val processes = mutableMapOf<String, RunningProcess>()

    /**
     * Starts runtime for a project, reusing an already-running process when available.
     */
    fun start(projectId: String, workingDir: String, config: RuntimeServerConfig = RuntimeServerConfig()): RuntimeStartResult {
        val existing = processes[projectId]
        if (existing != null && existing.handle.isAlive()) {
            return RuntimeStartResult(
                started = true,
                processId = existing.handle.id,
                command = existing.command,
                alreadyRunning = true,
                startupOutput = existing.handle.startupOutput(),
            )
        }

        val command = config.command.toMutableList().apply {
            addAll(config.extraArgs)
        }

        val handle = processLauncher.launch(command, workingDir)
        processes[projectId] = RunningProcess(handle, workingDir, config, command)

        return RuntimeStartResult(
            started = handle.isAlive(),
            processId = handle.id,
            command = command,
            alreadyRunning = false,
            startupOutput = handle.startupOutput(),
        )
    }

    /**
     * Stops runtime for a project.
     *
     * @return `true` when a running process existed and was stopped.
     */
    fun stop(projectId: String): Boolean {
        val existing = processes.remove(projectId) ?: return false
        existing.handle.stop()
        return true
    }

    /**
     * Restarts runtime for a project, preserving prior working directory/config when known.
     */
    fun restart(projectId: String): RuntimeStartResult {
        val existing = processes[projectId]
            ?: return start(projectId = projectId, workingDir = ".", config = RuntimeServerConfig())

        existing.handle.stop()
        processes.remove(projectId)
        return start(projectId, existing.workingDir, existing.config)
    }

    /**
     * Dispose-safe stop alias.
     */
    fun dispose(projectId: String): Boolean = stop(projectId)

    /**
     * Returns `true` when runtime process is alive for a project.
     */
    fun isRunning(projectId: String): Boolean = processes[projectId]?.handle?.isAlive() == true
}
