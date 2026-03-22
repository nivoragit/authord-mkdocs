package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.ProcessLauncher
import java.io.File
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.streams.toList

private const val PROCESS_OUTPUT_BUFFER_MAX_CHARS: Int = 200_000
private const val COMMAND_RUNNER_TIMEOUT_MILLIS: Long = 180_000L
private const val COMMAND_RUNNER_GRACEFUL_SHUTDOWN_MILLIS: Long = 2_000L
private const val PARENT_PIPE_GUARD_STOP_WAIT_MILLIS: Long = 500L

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

private data class ParentPipeGuard(
    private val process: Process,
    private val sentinelOutputStream: OutputStream,
) {
    private val stopped = AtomicBoolean(false)

    fun stop() {
        if (!stopped.compareAndSet(false, true)) {
            return
        }
        runCatching { process.destroyForcibly() }
        runCatching { sentinelOutputStream.close() }
        runCatching { process.waitFor(PARENT_PIPE_GUARD_STOP_WAIT_MILLIS, TimeUnit.MILLISECONDS) }
    }
}

private fun interface ParentPipeGuardLauncher {
    fun launch(targetPid: Long, workingDir: String): ParentPipeGuard?
}

private object ShellParentPipeGuardLauncher : ParentPipeGuardLauncher {
    override fun launch(targetPid: Long, workingDir: String): ParentPipeGuard? {
        val command = guardCommand(targetPid) ?: return null
        val process = runCatching {
            ProcessBuilder(command)
                .directory(File(workingDir))
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null
        return ParentPipeGuard(
            process = process,
            sentinelOutputStream = process.outputStream,
        )
    }

    private fun guardCommand(targetPid: Long): List<String>? {
        if (targetPid <= 0L) {
            return null
        }

        return if (isWindows()) {
            listOf(
                "cmd",
                "/d",
                "/c",
                "set /p _= & taskkill /PID $targetPid /T /F >NUL 2>NUL",
            )
        } else {
            listOf(
                "sh",
                "-c",
                "read _; kill -TERM -\$1 2>/dev/null; kill -TERM \$1 2>/dev/null",
                "--",
                targetPid.toString(),
            )
        }
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name").contains("win", ignoreCase = true)
    }
}

/**
 * Command runner backed by JVM process execution.
 */
class ProcessBuilderCommandRunner(
    private val processFactory: SystemProcessFactory = ProcessBuilderSystemProcessFactory(),
    private val waitTimeoutMillis: Long = COMMAND_RUNNER_TIMEOUT_MILLIS,
) : CommandRunner {
    /**
     * Runs command tokens and returns merged output + exit code.
     */
    override fun run(command: List<String>, workingDir: String): CommandResult {
        return try {
            val process = processFactory.start(command, workingDir, mergeErrorStream = true)
            val outputBuffer = CappedOutputBuffer(PROCESS_OUTPUT_BUFFER_MAX_CHARS)
            val outputLock = Any()
            val outputReader = thread(
                start = true,
                isDaemon = true,
                name = "authord-command-runner-output",
            ) {
                process.inputStream.bufferedReader().forEachLine { line ->
                    synchronized(outputLock) {
                        outputBuffer.appendLine(line)
                    }
                }
            }
            val completed = process.waitFor(waitTimeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) {
                destroyProcessTree(process, forcibly = false)
                if (!process.waitFor(COMMAND_RUNNER_GRACEFUL_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS)) {
                    destroyProcessTree(process, forcibly = true)
                }
            }
            outputReader.join(1_000L)
            val mergedOutput = synchronized(outputLock) { outputBuffer.snapshot() }
            if (!completed) {
                val timeoutMessage = "Command timed out after ${waitTimeoutMillis}ms"
                val stderr = if (mergedOutput.isBlank()) timeoutMessage else "$mergedOutput\n$timeoutMessage"
                return CommandResult(
                    exitCode = 1,
                    stdout = mergedOutput,
                    stderr = stderr,
                )
            }
            val exitCode = runCatching { process.exitValue() }.getOrDefault(1)
            CommandResult(
                exitCode = exitCode,
                stdout = mergedOutput,
                stderr = if (exitCode == 0) "" else mergedOutput,
            )
        } catch (exception: Exception) {
            if (exception is InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val message = exception.message ?: "Process execution failed"
            CommandResult(exitCode = 1, stderr = message)
        }
    }
}

private class ProcessBackedManagedProcessHandle(
    override val id: String,
    private val process: Process,
    private val stdoutBuffer: CappedOutputBuffer,
    private val stderrBuffer: CappedOutputBuffer,
    private val outputLock: Any,
    private val stdoutReaderThread: Thread,
    private val stderrReaderThread: Thread,
    private val parentPipeGuard: ParentPipeGuard?,
    private val parentGuardReleaseThread: Thread?,
    private val shutdownHookRegistrar: ShutdownHookRegistrar,
) : ManagedProcessHandle {
    private val shutdownHook: Thread = Thread(
        {
            parentPipeGuard?.stop()
            destroyProcessTree(process, forcibly = true)
        },
        "authord-$id-shutdown-hook",
    )
    private var shutdownHookRegistered: Boolean = shutdownHookRegistrar.register(shutdownHook)

    /**
     * Stops the spawned process and attempts bounded cleanup of output reader.
     */
    override fun stop() {
        unregisterShutdownHook()
        parentPipeGuard?.stop()
        destroyProcessTree(process, forcibly = false)
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                destroyProcessTree(process, forcibly = true)
            }
            stdoutReaderThread.join(500)
            stderrReaderThread.join(500)
            parentGuardReleaseThread?.join(PARENT_PIPE_GUARD_STOP_WAIT_MILLIS)
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
        stdoutBuffer.snapshot() + stderrBuffer.snapshot()
    }

    override fun stdoutOutput(): String = synchronized(outputLock) { stdoutBuffer.snapshot() }

    override fun stderrOutput(): String = synchronized(outputLock) { stderrBuffer.snapshot() }

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
        val stdoutBuffer = CappedOutputBuffer(PROCESS_OUTPUT_BUFFER_MAX_CHARS)
        val stderrBuffer = CappedOutputBuffer(PROCESS_OUTPUT_BUFFER_MAX_CHARS)
        val parentPipeGuard = runCatching { process.pid() }
            .getOrNull()
            ?.let { ShellParentPipeGuardLauncher.launch(targetPid = it, workingDir = workingDir) }

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

        val parentGuardReleaseThread = parentPipeGuard?.let { guard ->
            thread(
                start = true,
                isDaemon = true,
                name = "authord-$processId-parent-guard-release",
            ) {
                try {
                    process.waitFor()
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    guard.stop()
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
            parentPipeGuard = parentPipeGuard,
            parentGuardReleaseThread = parentGuardReleaseThread,
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

private class CappedOutputBuffer(
    private val maxChars: Int,
) {
    private val lines = ArrayDeque<String>()
    private var currentLength: Int = 0

    fun appendLine(line: String) {
        if (maxChars <= 0) {
            lines.clear()
            currentLength = 0
            return
        }

        val normalized = "$line\n"
        if (normalized.length >= maxChars) {
            lines.clear()
            val tail = normalized.takeLast(maxChars)
            lines.addLast(tail)
            currentLength = tail.length
            return
        }

        lines.addLast(normalized)
        currentLength += normalized.length
        while (currentLength > maxChars && lines.isNotEmpty()) {
            currentLength -= lines.removeFirst().length
        }
    }

    fun snapshot(): String {
        if (lines.isEmpty()) {
            return ""
        }
        val builder = StringBuilder(currentLength.coerceAtLeast(16))
        lines.forEach(builder::append)
        return builder.toString()
    }
}
