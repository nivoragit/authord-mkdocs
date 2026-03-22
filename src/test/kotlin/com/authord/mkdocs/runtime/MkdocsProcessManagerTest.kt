package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeHandle(
    override val id: String,
    private val startupOutputText: String = "",
) : ManagedProcessHandle {
    private var alive = true

    override fun stop() {
        alive = false
    }

    override fun isAlive(): Boolean = alive

    override fun startupOutput(): String = startupOutputText
}

class MkdocsProcessManagerTest {
    @Test
    fun `managed process handle default startup output is empty`() {
        val handle = object : ManagedProcessHandle {
            override val id: String = "default-handle"

            override fun stop() {
                // no-op
            }

            override fun isAlive(): Boolean = false
        }

        assertEquals("", handle.startupOutput())
    }

    @Test
    fun `runtime start result default startup output is empty`() {
        val result = RuntimeStartResult(
            started = true,
            processId = "process-1",
            command = listOf("mkdocs", "serve"),
            alreadyRunning = false,
        )

        assertEquals("", result.startupOutput)
    }

    @Test
    fun `ensures single server instance per project`() {
        var launchCount = 0
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ ->
            launchCount += 1
            FakeHandle("p-$launchCount")
        })

        val first = manager.start("project-1", "/tmp/project")
        val second = manager.start("project-1", "/tmp/project")

        assertTrue(first.started)
        assertTrue(second.alreadyRunning)
        assertEquals(1, launchCount)
    }

    @Test
    fun `restart stops previous process and launches a new one`() {
        var launchCount = 0
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ ->
            launchCount += 1
            FakeHandle("process-$launchCount")
        })

        val initial = manager.start("project-1", "/tmp/project")
        val restarted = manager.restart("project-1")

        assertEquals("process-1", initial.processId)
        assertEquals("process-2", restarted.processId)
        assertEquals(2, launchCount)
        assertTrue(manager.isRunning("project-1"))
    }

    @Test
    fun `stop returns false when no process exists`() {
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ -> FakeHandle("unused") })

        val stopped = manager.stop("missing-project")

        assertEquals(false, stopped)
    }

    @Test
    fun `stop and dispose return true for running process`() {
        var launchCount = 0
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ ->
            launchCount += 1
            FakeHandle("proc-$launchCount")
        })
        manager.start("project-2", "/tmp/project")

        assertEquals(true, manager.stop("project-2"))
        manager.start("project-3", "/tmp/project")
        assertEquals(true, manager.dispose("project-3"))
    }

    @Test
    fun `restart without existing process returns not started result`() {
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ ->
            throw AssertionError("restart should not launch a process when no prior runtime exists")
        })

        val restarted = manager.restart("project-missing")

        assertEquals("", restarted.processId)
        assertTrue(!restarted.started)
        assertEquals(emptyList(), restarted.command)
        assertTrue(restarted.startupOutput.contains("no tracked process"))
    }

    @Test
    fun `start appends caller supplied extra args`() {
        val manager = MkdocsProcessManager(ProcessLauncher { command, _ ->
            assertEquals(listOf("mkdocs", "serve", "--dirtyreload"), command)
            FakeHandle("process-extra")
        })

        val start = manager.start(
            projectId = "project-4",
            workingDir = "/tmp/project",
            config = RuntimeServerConfig(extraArgs = listOf("--dirtyreload")),
        )

        assertEquals("process-extra", start.processId)
        assertEquals(listOf("mkdocs", "serve", "--dirtyreload"), start.command)
    }

    @Test
    fun `start result includes captured startup output`() {
        val manager = MkdocsProcessManager(ProcessLauncher { _, _ ->
            FakeHandle("process-output", startupOutputText = "ready at http://127.0.0.1:8000/")
        })

        val start = manager.start("project-5", "/tmp/project")

        assertEquals("ready at http://127.0.0.1:8000/", start.startupOutput)
    }
}
