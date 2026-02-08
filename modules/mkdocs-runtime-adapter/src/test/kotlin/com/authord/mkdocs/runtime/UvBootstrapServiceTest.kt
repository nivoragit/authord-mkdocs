package com.authord.mkdocs.runtime

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UvBootstrapServiceTest {
    @Test
    fun `runs bootstrap and install commands on first activation`() {
        val commands = mutableListOf<List<String>>()
        val service = UvBootstrapService { command, _ ->
            commands += command
            CommandResult(exitCode = 0)
        }

        val result = service.bootstrap("/tmp/project")
        val expectedRuntimePath = Path.of("/tmp/project").resolve(".mkdocs-plugin-venv").toString()

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(listOf("uv", "venv", expectedRuntimePath), commands[0])
        assertEquals(listOf("uv", "pip", "install", "mkdocs"), commands[1])
        assertEquals(expectedRuntimePath, result.runtimePath)
    }

    @Test
    fun `skips bootstrap for already prepared project`() {
        val calls = mutableListOf<List<String>>()
        val service = UvBootstrapService { command, _ ->
            calls += command
            CommandResult(0)
        }

        service.bootstrap("/tmp/project")
        val second = service.bootstrap("/tmp/project")

        assertTrue(second.success)
        assertTrue(second.skipped)
        assertEquals(2, calls.size)
    }

    @Test
    fun `returns failure when setup command fails`() {
        val service = UvBootstrapService { command, _ ->
            if (command[1] == "venv") {
                CommandResult(exitCode = 1, stderr = "uv venv failed")
            } else {
                CommandResult(exitCode = 0)
            }
        }

        val result = service.bootstrap("/tmp/project-fail-setup")

        assertFalse(result.success)
        assertEquals("uv venv failed", result.errorMessage)
        assertEquals(false, result.skipped)
    }

    @Test
    fun `returns failure when install command fails`() {
        val service = UvBootstrapService { command, _ ->
            if (command[1] == "pip") {
                CommandResult(exitCode = 1, stderr = "install failed")
            } else {
                CommandResult(exitCode = 0)
            }
        }

        val result = service.bootstrap("/tmp/project-fail-install")

        assertFalse(result.success)
        assertEquals("install failed", result.errorMessage)
        assertEquals(2, result.executedCommands.size)
    }
}
