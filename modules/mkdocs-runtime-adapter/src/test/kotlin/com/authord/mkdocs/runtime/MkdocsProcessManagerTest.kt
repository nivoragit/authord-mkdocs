package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeHandle(override val id: String) : ManagedProcessHandle {
    private var alive = true

    override fun stop() {
        alive = false
    }

    override fun isAlive(): Boolean = alive
}

class MkdocsProcessManagerTest {
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
    fun `restart without existing process falls back to defaults and current dir`() {
        val manager = MkdocsProcessManager(ProcessLauncher { command, workingDir ->
            assertEquals(".", workingDir)
            assertEquals(listOf("mkdocs", "serve"), command)
            FakeHandle("process-default")
        })

        val restarted = manager.restart("project-missing")

        assertEquals("process-default", restarted.processId)
        assertTrue(restarted.started)
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
}
