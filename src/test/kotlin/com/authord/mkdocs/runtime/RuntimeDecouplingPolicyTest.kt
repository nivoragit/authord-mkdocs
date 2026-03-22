package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeDecouplingPolicyTest {
    @Test
    fun `default runtime command does not hardcode host or port`() {
        var capturedCommand: List<String> = emptyList()
        val manager = MkdocsProcessManager(
            processLauncher = ProcessLauncher { command, _ ->
                capturedCommand = command
                object : ManagedProcessHandle {
                    override val id: String = "proc-default"
                    override fun stop() = Unit
                    override fun isAlive(): Boolean = true
                }
            },
        )

        val result = manager.start(projectId = "project-default", workingDir = "/tmp/project")

        assertTrue(result.started)
        assertEquals(listOf("mkdocs", "serve"), capturedCommand)
        assertFalse(capturedCommand.any { it.contains("127.0.0.1") || it.contains(":8000") })
        assertFalse(capturedCommand.any { it == "--dev-addr" || it == "--host" || it == "--port" })
    }

    @Test
    fun `manager preserves caller-defined command without injecting runtime endpoint`() {
        var capturedCommand: List<String> = emptyList()
        val manager = MkdocsProcessManager(
            processLauncher = ProcessLauncher { command, _ ->
                capturedCommand = command
                object : ManagedProcessHandle {
                    override val id: String = "proc-custom"
                    override fun stop() = Unit
                    override fun isAlive(): Boolean = true
                }
            },
        )

        val result = manager.start(
            projectId = "project-custom",
            workingDir = "/tmp/project",
            config = RuntimeServerConfig(
                command = listOf("mkdocs", "serve", "--dev-addr", "0.0.0.0:9000"),
                extraArgs = listOf("--dirtyreload"),
            ),
        )

        assertTrue(result.started)
        assertEquals(
            listOf("mkdocs", "serve", "--dev-addr", "0.0.0.0:9000", "--dirtyreload"),
            capturedCommand,
        )
    }
}
