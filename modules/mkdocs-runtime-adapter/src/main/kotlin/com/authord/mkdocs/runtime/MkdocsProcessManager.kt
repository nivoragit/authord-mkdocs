package com.authord.mkdocs.runtime

data class RuntimeServerConfig(
    val bind: String = "127.0.0.1",
    val port: Int = 8000,
    val dirtyReload: Boolean = true,
)

interface ManagedProcessHandle {
    val id: String
    fun stop()
    fun isAlive(): Boolean
}

fun interface ProcessLauncher {
    fun launch(command: List<String>, workingDir: String): ManagedProcessHandle
}

data class RuntimeStartResult(
    val started: Boolean,
    val processId: String,
    val command: List<String>,
    val alreadyRunning: Boolean,
)

private data class RunningProcess(
    val handle: ManagedProcessHandle,
    val workingDir: String,
    val config: RuntimeServerConfig,
    val command: List<String>,
)

class MkdocsProcessManager(
    private val processLauncher: ProcessLauncher,
) {
    private val processes = mutableMapOf<String, RunningProcess>()

    fun start(projectId: String, workingDir: String, config: RuntimeServerConfig = RuntimeServerConfig()): RuntimeStartResult {
        val existing = processes[projectId]
        if (existing != null && existing.handle.isAlive()) {
            return RuntimeStartResult(
                started = true,
                processId = existing.handle.id,
                command = existing.command,
                alreadyRunning = true,
            )
        }

        val command = mutableListOf("mkdocs", "serve", "--dev-addr", "${config.bind}:${config.port}")
        if (config.dirtyReload) {
            command += "--dirtyreload"
        }

        val handle = processLauncher.launch(command, workingDir)
        processes[projectId] = RunningProcess(handle, workingDir, config, command)

        return RuntimeStartResult(
            started = handle.isAlive(),
            processId = handle.id,
            command = command,
            alreadyRunning = false,
        )
    }

    fun stop(projectId: String): Boolean {
        val existing = processes.remove(projectId) ?: return false
        existing.handle.stop()
        return true
    }

    fun restart(projectId: String): RuntimeStartResult {
        val existing = processes[projectId]
            ?: return start(projectId = projectId, workingDir = ".", config = RuntimeServerConfig())

        existing.handle.stop()
        processes.remove(projectId)
        return start(projectId, existing.workingDir, existing.config)
    }

    fun dispose(projectId: String): Boolean = stop(projectId)

    fun isRunning(projectId: String): Boolean = processes[projectId]?.handle?.isAlive() == true
}
