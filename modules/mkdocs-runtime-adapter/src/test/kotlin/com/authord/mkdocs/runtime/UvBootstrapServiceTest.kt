package com.authord.mkdocs.runtime

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UvBootstrapServiceTest {
    @Test
    fun `command result exposes stdout and stderr properties`() {
        val result = CommandResult(exitCode = 7, stdout = "ok", stderr = "err")

        assertEquals(7, result.exitCode)
        assertEquals("ok", result.stdout)
        assertEquals("err", result.stderr)
    }

    @Test
    fun `runs bootstrap and install commands on first activation`() {
        val commands = mutableListOf<List<String>>()
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            },
            uvExecutableProvider = StaticUvExecutableProvider(),
        )

        val result = service.bootstrap("/tmp/project")
        val expectedRuntimePath = Path.of("/tmp/project").resolve(".mkdocs-plugin-venv").toString()

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals("uv", result.uvExecutablePath)
        assertEquals(listOf("uv", "venv", expectedRuntimePath), commands[0])
        assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
        assertEquals(expectedRuntimePath, result.runtimePath)
    }

    @Test
    fun `uses resolved uv executable path for all commands`() {
        val commands = mutableListOf<List<String>>()
        val projectPath = "/tmp/custom-uv-project"
        val expectedRuntimePath = Path.of(projectPath).resolve(".mkdocs-plugin-venv").toString()
        val customUv = "/tmp/authord-runtime-tools/uv"
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            },
            uvExecutableProvider = StaticUvExecutableProvider(customUv),
        )

        val result = service.bootstrap(projectPath)

        assertTrue(result.success)
        assertEquals(customUv, result.uvExecutablePath)
        assertEquals(listOf(customUv, "venv", expectedRuntimePath), commands[0])
        assertEquals(listOf(customUv, "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
    }

    @Test
    fun `fails bootstrap when uv executable cannot be resolved`() {
        val service = UvBootstrapService(
            commandRunner = { _, _ -> CommandResult(exitCode = 0) },
            uvExecutableProvider = UvExecutableProvider {
                UvExecutableResult(
                    success = false,
                    errorMessage = "Unable to find uv executable",
                )
            },
        )

        val result = service.bootstrap("/tmp/project")

        assertFalse(result.success)
        assertEquals("", result.uvExecutablePath)
        assertTrue(result.errorMessage.contains("Unable to find uv"))
        assertTrue(result.executedCommands.isEmpty())
    }

    @Test
    fun `reuses existing runtime directory and skips uv venv command`() {
        val tempProject = createTempDirectory(prefix = "uv-bootstrap-existing-runtime-")
        try {
            val existingRuntime = tempProject.resolve(".mkdocs-plugin-venv")
            existingRuntime.createDirectories()

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(tempProject.toString())
            val expectedRuntimePath = tempProject.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(1, commands.size)
            assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands.single())
            assertEquals(existingRuntime.toString(), result.runtimePath)
        } finally {
            tempProject.toFile().deleteRecursively()
        }
    }

    @Test
    fun `continues when setup reports existing virtual environment and installs mkdocs`() {
        val commands = mutableListOf<List<String>>()
        val projectPath = "/tmp/project-existing-race"
        val expectedRuntimePath = Path.of(projectPath).resolve(".mkdocs-plugin-venv").toString()
        val service = UvBootstrapService { command, _ ->
            commands += command
            if (command[1] == "venv") {
                CommandResult(
                    exitCode = 2,
                    stderr = "A virtual environment already exists at '$expectedRuntimePath'",
                )
            } else {
                CommandResult(exitCode = 0)
            }
        }

        val result = service.bootstrap(projectPath)

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(2, commands.size)
        assertEquals("venv", commands[0][1])
        assertEquals("pip", commands[1][1])
        assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
    }

    @Test
    fun `continues when setup reports existing virtual environment in stdout`() {
        val commands = mutableListOf<List<String>>()
        val projectPath = "/tmp/project-existing-race-stdout"
        val expectedRuntimePath = Path.of(projectPath).resolve(".mkdocs-plugin-venv").toString()
        val service = UvBootstrapService { command, _ ->
            commands += command
            if (command[1] == "venv") {
                CommandResult(
                    exitCode = 2,
                    stdout = "error: Failed to create virtual environment\n  Caused by: A virtual environment already exists at `$expectedRuntimePath`",
                    stderr = "",
                )
            } else {
                CommandResult(exitCode = 0)
            }
        }

        val result = service.bootstrap(projectPath)

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals(2, commands.size)
        assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
    }

    @Test
    fun `installs mkdocs material when mkdocs config uses material theme`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-material-theme-")
        try {
            val mkdocsConfig = projectRoot.resolve("mkdocs.yml")
            mkdocsConfig.writeText(
                """
                site_name: Demo
                theme:
                  name: material
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(2, commands.size)
            assertEquals(listOf("uv", "venv", runtimePath), commands[0])
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs", "mkdocs-material"),
                commands[1],
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `installs mkdocs material when theme uses inline scalar value`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-inline-scalar-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                theme: material
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs", "mkdocs-material"),
                commands.last(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `installs mkdocs material when theme uses inline map value`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-inline-map-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                theme: { name: material, language: en }
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs", "mkdocs-material"),
                commands.last(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not install mkdocs material for non-material inline declarations`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-inline-non-material-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                theme: mkdocs
                theme: { palette: slate }
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs"),
                commands.last(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not install mkdocs material when theme block is empty`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-empty-theme-block-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                theme:
                
                nav:
                  - Home: index.md
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs"),
                commands.last(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `re-runs package install when required package set expands`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-package-refresh-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            val mkdocsConfig = projectRoot.resolve("mkdocs.yml")
            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val first = service.bootstrap(projectRoot.toString())
            assertTrue(first.success)
            assertFalse(first.skipped)
            assertEquals(1, commands.size)

            mkdocsConfig.writeText(
                """
                site_name: Demo
                theme:
                  name: material
                """.trimIndent() + "\n",
            )

            val second = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(second.success)
            assertFalse(second.skipped)
            assertEquals(2, commands.size)
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs", "mkdocs-material"),
                commands.last(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
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
