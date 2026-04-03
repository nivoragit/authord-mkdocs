package com.authord.mkdocs.ui.intellij

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeProcess(
    output: String,
    errorOutput: String = "",
    private val exitCodeValue: Int,
    initiallyAlive: Boolean = true,
    private val waitForTimeoutResult: Boolean = true,
) : Process() {
    private val stdout = ByteArrayInputStream(output.toByteArray())
    private val stderr = ByteArrayInputStream(errorOutput.toByteArray())
    private val stdin = ByteArrayOutputStream()
    private var alive = initiallyAlive
    var destroyCalls: Int = 0
        private set
    var destroyForciblyCalls: Int = 0
        private set

    override fun getOutputStream(): OutputStream = stdin

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int {
        alive = false
        return exitCodeValue
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        if (waitForTimeoutResult) {
            alive = false
        }
        return waitForTimeoutResult
    }

    override fun exitValue(): Int = exitCodeValue

    override fun destroy() {
        destroyCalls += 1
        alive = false
    }

    override fun destroyForcibly(): Process {
        destroyForciblyCalls += 1
        alive = false
        return this
    }

    override fun isAlive(): Boolean = alive
}

private class RecordingProcessFactory(
    private val process: Process,
) : SystemProcessFactory {
    var command: List<String> = emptyList()
        private set
    var workingDir: String = ""
        private set
    var mergeErrorStream: Boolean = false
        private set

    override fun start(command: List<String>, workingDir: String, mergeErrorStream: Boolean): Process {
        this.command = command
        this.workingDir = workingDir
        this.mergeErrorStream = mergeErrorStream
        return process
    }
}

private class RecordingShutdownHookRegistrar : ShutdownHookRegistrar {
    val registered = mutableListOf<Thread>()
    val unregistered = mutableListOf<Thread>()

    override fun register(hook: Thread): Boolean {
        registered += hook
        return true
    }

    override fun unregister(hook: Thread): Boolean {
        unregistered += hook
        return true
    }
}

class SystemRuntimeAdaptersTest {
    @Test
    fun `command runner executes command and captures stdout output`() {
        val process = FakeProcess(output = "uv ok", exitCodeValue = 0)
        val factory = RecordingProcessFactory(process)
        val runner = ProcessBuilderCommandRunner(factory)

        val result = runner.run(command = listOf("uv", "--version"), workingDir = "/tmp/project")

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("uv ok"))
        assertEquals("", result.stderr)
        assertEquals(listOf("uv", "--version"), factory.command)
        assertEquals("/tmp/project", factory.workingDir)
        assertFalse(factory.mergeErrorStream)
    }

    @Test
    fun `command runner captures stderr separately from stdout`() {
        val process = FakeProcess(output = "out line", errorOutput = "err line", exitCodeValue = 0)
        val runner = ProcessBuilderCommandRunner(RecordingProcessFactory(process))

        val result = runner.run(command = listOf("uv", "pip", "install"), workingDir = "/tmp/project")

        assertTrue(result.stdout.contains("out line"))
        assertTrue(result.stderr.contains("err line"))
    }

    @Test
    fun `command runner returns failure envelope when process start throws`() {
        val runner = ProcessBuilderCommandRunner(
            processFactory = SystemProcessFactory { _, _, _ ->
                throw IllegalStateException("cannot start process")
            },
        )

        val result = runner.run(command = listOf("uv", "--version"), workingDir = "/tmp/project")

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("cannot start process"))
    }

    @Test
    fun `command runner times out long-running command and terminates process`() {
        val process = FakeProcess(
            output = "still running",
            exitCodeValue = 0,
            initiallyAlive = true,
            waitForTimeoutResult = false,
        )
        val factory = RecordingProcessFactory(process)
        val runner = ProcessBuilderCommandRunner(
            processFactory = factory,
            waitTimeoutMillis = 1L,
        )

        val result = runner.run(command = listOf("uv", "pip", "install"), workingDir = "/tmp/project")

        assertEquals(1, result.exitCode)
        assertTrue(result.stderr.contains("timed out"))
        assertTrue(process.destroyCalls >= 1 || process.destroyForciblyCalls >= 1)
    }

    @Test
    fun `process launcher captures startup output from process stream`() {
        val process = FakeProcess(
            output = "Serving on http://127.0.0.1:8000/\n",
            exitCodeValue = 0,
            initiallyAlive = true,
        )
        val factory = RecordingProcessFactory(process)
        val launcher = ProcessBuilderProcessLauncher(
            processFactory = factory,
            startupWaitMillis = 500L,
            pollIntervalMillis = 10L,
        )

        val handle = launcher.launch(command = listOf("mkdocs", "serve"), workingDir = "/tmp/project")

        assertTrue(handle.startupOutput().contains("http://127.0.0.1:8000/"))
        assertTrue(handle.isAlive())
        assertEquals(listOf("mkdocs", "serve"), factory.command)
        assertFalse(factory.mergeErrorStream)

        handle.stop()
        assertFalse(handle.isAlive())
    }

    @Test
    fun `process launcher returns empty startup output when none is emitted`() {
        val process = FakeProcess(
            output = "",
            exitCodeValue = 0,
            initiallyAlive = true,
        )
        val launcher = ProcessBuilderProcessLauncher(
            processFactory = RecordingProcessFactory(process),
            startupWaitMillis = 50L,
            pollIntervalMillis = 10L,
        )

        val handle = launcher.launch(command = listOf("mkdocs", "serve"), workingDir = "/tmp/project")

        assertEquals("", handle.startupOutput())
        handle.stop()
        assertFalse(handle.isAlive())
    }

    @Test
    fun `process launcher registers and unregisters shutdown hook around handle lifecycle`() {
        val process = FakeProcess(
            output = "Serving on http://127.0.0.1:8000/\n",
            exitCodeValue = 0,
            initiallyAlive = true,
        )
        val shutdownHooks = RecordingShutdownHookRegistrar()
        val launcher = ProcessBuilderProcessLauncher(
            processFactory = RecordingProcessFactory(process),
            startupWaitMillis = 200L,
            pollIntervalMillis = 10L,
            shutdownHookRegistrar = shutdownHooks,
        )

        val handle = launcher.launch(command = listOf("mkdocs", "serve"), workingDir = "/tmp/project")

        assertEquals(1, shutdownHooks.registered.size)
        val registeredHook = shutdownHooks.registered.single()
        handle.stop()
        assertEquals(listOf(registeredHook), shutdownHooks.unregistered)
    }

    @Test
    fun `registered shutdown hook forcibly stops process when invoked`() {
        val process = FakeProcess(
            output = "Serving on http://127.0.0.1:8000/\n",
            exitCodeValue = 0,
            initiallyAlive = true,
        )
        val shutdownHooks = RecordingShutdownHookRegistrar()
        val launcher = ProcessBuilderProcessLauncher(
            processFactory = RecordingProcessFactory(process),
            startupWaitMillis = 200L,
            pollIntervalMillis = 10L,
            shutdownHookRegistrar = shutdownHooks,
        )

        launcher.launch(command = listOf("mkdocs", "serve"), workingDir = "/tmp/project")
        val registeredHook = shutdownHooks.registered.single()
        registeredHook.run()

        assertEquals(1, process.destroyForciblyCalls)
    }

    @Test
    fun `stop forcibly destroys process when graceful shutdown times out`() {
        val process = FakeProcess(
            output = "Serving on http://127.0.0.1:8000/\n",
            exitCodeValue = 0,
            initiallyAlive = true,
            waitForTimeoutResult = false,
        )
        val shutdownHooks = RecordingShutdownHookRegistrar()
        val launcher = ProcessBuilderProcessLauncher(
            processFactory = RecordingProcessFactory(process),
            startupWaitMillis = 200L,
            pollIntervalMillis = 10L,
            shutdownHookRegistrar = shutdownHooks,
        )

        val handle = launcher.launch(command = listOf("mkdocs", "serve"), workingDir = "/tmp/project")
        handle.stop()

        assertTrue(process.destroyCalls >= 1)
        assertTrue(process.destroyForciblyCalls >= 1)
    }
}
