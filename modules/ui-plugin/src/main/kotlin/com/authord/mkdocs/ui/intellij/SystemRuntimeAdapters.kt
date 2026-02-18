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
    private val outputBuffer: StringBuilder,
    private val outputLock: Any,
    private val outputReaderThread: Thread,
    private val shutdownHookRegistrar: ShutdownHookRegistrar,
) : ManagedProcessHandle {
    private val shutdownHook: Thread = Thread(
        { destroyProcessTree(process, forcibly = true) },
        "authord-mkdocs-$id-shutdown-hook",
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
            outputReaderThread.join(500)
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
    override fun startupOutput(): String = synchronized(outputLock) { outputBuffer.toString() }

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
 * Process launcher that captures runtime startup output for base-URL detection.
 */
class ProcessBuilderProcessLauncher(
    private val processFactory: SystemProcessFactory = ProcessBuilderSystemProcessFactory(),
    private val startupWaitMillis: Long = 30_000L,
    private val pollIntervalMillis: Long = 50L,
    private val shutdownHookRegistrar: ShutdownHookRegistrar = RuntimeShutdownHookRegistrar,
) : ProcessLauncher {
    private val counter = AtomicInteger(0)
    private val urlRegex = Regex("(https?://[^\\s]+)")

    /**
     * Launches a process and captures startup output with a bounded wait window.
     */
    override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
        val process = processFactory.start(command, workingDir, mergeErrorStream = true)
        val processId = "process-${counter.incrementAndGet()}"
        val outputLock = Any()
        val outputBuffer = StringBuilder()

        val readerThread = thread(
            start = true,
            isDaemon = true,
            name = "authord-mkdocs-$processId-output-reader",
        ) {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(outputLock) {
                    outputBuffer.appendLine(line)
                }
            }
        }

        waitForStartupOutput(process, outputBuffer, outputLock)
        waitForReaderDrainIfProcessExited(process, readerThread)

        return ProcessBackedManagedProcessHandle(
            id = processId,
            process = process,
            outputBuffer = outputBuffer,
            outputLock = outputLock,
            outputReaderThread = readerThread,
            shutdownHookRegistrar = shutdownHookRegistrar,
        )
    }

    private fun waitForStartupOutput(process: Process, outputBuffer: StringBuilder, outputLock: Any) {
        val deadline = System.currentTimeMillis() + startupWaitMillis

        while (System.currentTimeMillis() < deadline) {
            val currentOutput = synchronized(outputLock) { outputBuffer.toString() }
            if (urlRegex.containsMatchIn(currentOutput)) {
                return
            }

            if (!process.isAlive && currentOutput.isNotBlank()) {
                return
            }

            try {
                Thread.sleep(pollIntervalMillis)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun waitForReaderDrainIfProcessExited(process: Process, outputReaderThread: Thread) {
        if (process.isAlive) {
            return
        }

        try {
            outputReaderThread.join(250)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
