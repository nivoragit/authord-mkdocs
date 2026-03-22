package com.authord.mkdocs.runtime

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

    /**
     * Returns captured stdout text, if available.
     */
    fun stdoutOutput(): String = startupOutput()

    /**
     * Returns captured stderr text, if available.
     */
    fun stderrOutput(): String = ""

    /**
     * Returns process exit code when available.
     */
    fun exitCodeOrNull(): Int? = null
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

/**
 * Runtime process diagnostics snapshot for startup/readiness troubleshooting.
 */
data class RuntimeProcessDiagnostics(
    val processId: String,
    val command: List<String>,
    val isAlive: Boolean,
    val startupOutput: String,
    val stdoutOutput: String,
    val stderrOutput: String,
    val exitCode: Int?,
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
    private val processes = ConcurrentHashMap<String, RunningProcess>()
    private val stateLock = ReentrantLock()

    /**
     * Starts runtime for a project, reusing an already-running process when available.
     */
    fun start(projectId: String, workingDir: String, config: RuntimeServerConfig = RuntimeServerConfig()): RuntimeStartResult {
        return stateLock.withLock {
            startLocked(projectId, workingDir, config)
        }
    }

    private fun startLocked(
        projectId: String,
        workingDir: String,
        config: RuntimeServerConfig,
    ): RuntimeStartResult {
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

        val command = buildCommand(config)

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
        return stateLock.withLock {
            val existing = processes.remove(projectId) ?: return@withLock false
            existing.handle.stop()
            true
        }
    }

    /**
     * Restarts runtime for a project, preserving prior working directory/config when known.
     */
    fun restart(projectId: String): RuntimeStartResult {
        return stateLock.withLock {
            val existing = processes[projectId]
                ?: return@withLock RuntimeStartResult(
                    started = false,
                    processId = "",
                    command = emptyList(),
                    alreadyRunning = false,
                    startupOutput = "Cannot restart runtime: no tracked process for project '$projectId'.",
                )

            existing.handle.stop()
            processes.remove(projectId)
            startLocked(projectId, existing.workingDir, existing.config)
        }
    }

    /**
     * Dispose-safe stop alias.
     */
    fun dispose(projectId: String): Boolean = stop(projectId)

    /**
     * Returns `true` when runtime process is alive for a project.
     */
    fun isRunning(projectId: String): Boolean = stateLock.withLock {
        processes[projectId]?.handle?.isAlive() == true
    }

    /**
     * Returns current process diagnostics for a project when a handle is tracked.
     */
    fun diagnostics(projectId: String): RuntimeProcessDiagnostics? {
        return stateLock.withLock {
            val running = processes[projectId] ?: return@withLock null
            val handle = running.handle
            RuntimeProcessDiagnostics(
                processId = handle.id,
                command = running.command,
                isAlive = handle.isAlive(),
                startupOutput = handle.startupOutput(),
                stdoutOutput = handle.stdoutOutput(),
                stderrOutput = handle.stderrOutput(),
                exitCode = handle.exitCodeOrNull(),
            )
        }
    }

    /**
     * Builds effective runtime command without injecting host/port defaults.
     *
     * Runtime endpoint selection remains decoupled from the plugin by relying on
     * process stdout URL discovery instead of hardcoded host/port assumptions.
     */
    private fun buildCommand(config: RuntimeServerConfig): List<String> {
        val sanitizedBase = config.command.map { it.trim() }.filter { it.isNotEmpty() }
        val sanitizedExtras = config.extraArgs.map { it.trim() }.filter { it.isNotEmpty() }
        val baseCommand = if (sanitizedBase.isEmpty()) listOf("mkdocs", "serve") else sanitizedBase
        return baseCommand + sanitizedExtras
    }
}
