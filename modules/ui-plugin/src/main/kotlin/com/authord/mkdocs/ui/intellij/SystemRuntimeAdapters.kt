package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.ProcessLauncher
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.streams.toList

/**
 * Registers JVM shutdown hooks for process cleanup.
 */
interface ShutdownHookRegistrar {
    /**
     * Registers one shutdown hook thread.
     *
     * @return `true` when hook registration succeeded.
     */
    fun register(hook: Thread): Boolean

    /**
     * Unregisters one shutdown hook thread.
     */
    fun unregister(hook: Thread): Boolean
}

/**
 * Default shutdown-hook registrar backed by [Runtime].
 */
object RuntimeShutdownHookRegistrar : ShutdownHookRegistrar {
    /**
     * Registers a shutdown hook with best-effort error handling.
     */
    override fun register(hook: Thread): Boolean {
        return try {
            Runtime.getRuntime().addShutdownHook(hook)
            true
        } catch (ignored: IllegalStateException) {
            false
        } catch (ignored: SecurityException) {
            false
        }
    }

    /**
     * Unregisters a previously registered shutdown hook.
     */
    override fun unregister(hook: Thread): Boolean {
        return try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (ignored: IllegalStateException) {
            false
        } catch (ignored: SecurityException) {
            false
        }
    }
}

/**
 * Factory for starting OS-level processes used by runtime adapters.
 */
fun interface SystemProcessFactory {
    /**
     * Starts a process command from the provided working directory.
     */
    fun start(command: List<String>, workingDir: String, mergeErrorStream: Boolean): Process
}

/**
 * Default JVM process factory using [ProcessBuilder].
 */
class ProcessBuilderSystemProcessFactory : SystemProcessFactory {
    /**
     * Starts a process via [ProcessBuilder] without shell wrappers.
     */
    override fun start(command: List<String>, workingDir: String, mergeErrorStream: Boolean): Process {
        val processBuilder = ProcessBuilder(command)
            .directory(File(workingDir))
            .redirectErrorStream(mergeErrorStream)
        
        processBuilder.environment()["PYTHONWARNINGS"] = "ignore"
        
        return processBuilder.start()
    }
}

/**
 * Command runner backed by JVM process execution.
 */
class ProcessBuilderCommandRunner(
    private val processFactory: SystemProcessFactory = ProcessBuilderSystemProcessFactory(),
) : CommandRunner {
    /**
     * Runs command tokens and returns merged output + exit code.
     */
    override fun run(command: List<String>, workingDir: String): CommandResult {
        return try {
            val process = processFactory.start(command, workingDir, mergeErrorStream = true)
            val mergedOutput = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            CommandResult(
                exitCode = exitCode,
                stdout = mergedOutput,
                stderr = if (exitCode == 0) "" else mergedOutput,
            )
        } catch (exception: Exception) {
            val message = exception.message ?: "Process execution failed"
            CommandResult(exitCode = 1, stderr = message)
        }
    }
}

private class ProcessBackedManagedProcessHandle(
    override val id: String,
    private val process: Process,
    private val stdoutBuffer: StringBuilder,
    private val stderrBuffer: StringBuilder,
    private val outputLock: Any,
    private val stdoutReaderThread: Thread,
    private val stderrReaderThread: Thread,
    private val shutdownHookRegistrar: ShutdownHookRegistrar,
) : ManagedProcessHandle {
    private val shutdownHook: Thread = Thread(
        { destroyProcessTree(process, forcibly = true) },
        "authord-$id-shutdown-hook",
    )
    private var shutdownHookRegistered: Boolean = shutdownHookRegistrar.register(shutdownHook)

    /**
     * Stops the spawned process and attempts bounded cleanup of output reader.
     */
    override fun stop() {
        unregisterShutdownHook()
        destroyProcessTree(process, forcibly = false)
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                destroyProcessTree(process, forcibly = true)
            }
            stdoutReaderThread.join(500)
            stderrReaderThread.join(500)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
            destroyProcessTree(process, forcibly = true)
        }
    }

    /**
     * Returns whether the process is still alive.
     */
    override fun isAlive(): Boolean = process.isAlive

    /**
     * Returns currently captured startup/runtime output snapshot.
     */
    override fun startupOutput(): String = synchronized(outputLock) {
        buildString {
            append(stdoutBuffer)
            append(stderrBuffer)
        }
    }

    override fun stdoutOutput(): String = synchronized(outputLock) { stdoutBuffer.toString() }

    override fun stderrOutput(): String = synchronized(outputLock) { stderrBuffer.toString() }

    override fun exitCodeOrNull(): Int? {
        return if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull()
    }

    private fun unregisterShutdownHook() {
        if (!shutdownHookRegistered) {
            return
        }
        shutdownHookRegistered = false
        shutdownHookRegistrar.unregister(shutdownHook)
    }
}

private fun destroyProcessTree(process: Process, forcibly: Boolean) {
    val root = try {
        process.toHandle()
    } catch (ignored: UnsupportedOperationException) {
        if (forcibly) {
            process.destroyForcibly()
        } else {
            process.destroy()
        }
        return
    }

    val descendants = root.descendants().toList().asReversed()
    descendants.forEach { handle ->
        runCatching {
            if (forcibly) handle.destroyForcibly() else handle.destroy()
        }
    }
    runCatching {
        if (forcibly) root.destroyForcibly() else root.destroy()
    }
}

/**
 * Process launcher that captures and continuously drains runtime stdout/stderr.
 */
class ProcessBuilderProcessLauncher(
    private val processFactory: SystemProcessFactory = ProcessBuilderSystemProcessFactory(),
    private val startupWaitMillis: Long = 30_000L,
    private val pollIntervalMillis: Long = 50L,
    private val shutdownHookRegistrar: ShutdownHookRegistrar = RuntimeShutdownHookRegistrar,
) : ProcessLauncher {
    private val counter = AtomicInteger(0)

    /**
     * Launches a process and begins non-blocking stdout/stderr draining immediately.
     */
    override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
        val process = processFactory.start(command, workingDir, mergeErrorStream = false)
        val processId = "process-${counter.incrementAndGet()}"
        val outputLock = Any()
        val stdoutBuffer = StringBuilder()
        val stderrBuffer = StringBuilder()

        val stdoutReaderThread = thread(
            start = true,
            isDaemon = true,
            name = "authord-$processId-stdout-reader",
        ) {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(outputLock) {
                    stdoutBuffer.appendLine(line)
                }
            }
        }

        val stderrReaderThread = thread(
            start = true,
            isDaemon = true,
            name = "authord-$processId-stderr-reader",
        ) {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(outputLock) {
                    stderrBuffer.appendLine(line)
                }
            }
        }

        waitForReaderDrainIfProcessExited(process, stdoutReaderThread, stderrReaderThread)

        return ProcessBackedManagedProcessHandle(
            id = processId,
            process = process,
            stdoutBuffer = stdoutBuffer,
            stderrBuffer = stderrBuffer,
            outputLock = outputLock,
            stdoutReaderThread = stdoutReaderThread,
            stderrReaderThread = stderrReaderThread,
            shutdownHookRegistrar = shutdownHookRegistrar,
        )
    }

    private fun waitForReaderDrainIfProcessExited(process: Process, stdoutReaderThread: Thread, stderrReaderThread: Thread) {
        if (process.isAlive) {
            return
        }

        val joinMillis = startupWaitMillis
            .coerceAtMost(500L)
            .coerceAtLeast(pollIntervalMillis.coerceAtLeast(25L))
        try {
            stdoutReaderThread.join(joinMillis)
            stderrReaderThread.join(joinMillis)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
