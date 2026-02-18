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
        // Step 1: create venv
        assertEquals(listOf("uv", "venv", expectedRuntimePath), commands[0])
        // Step 2: install mkdocs base
        assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
        // Step 3: mkdocs get-deps
        assertTrue(commands[2].contains("get-deps"))
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
            // No venv command, just install + get-deps
            assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[0])
            assertTrue(commands[1].contains("get-deps"))
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
        assertEquals(listOf("uv", "pip", "install", "--python", expectedRuntimePath, "mkdocs"), commands[1])
    }

    @Test
    fun `installs dependencies discovered by mkdocs get-deps`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-get-deps-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                theme:
                  name: material
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                // Simulate mkdocs get-deps returning dependency list
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 0, stdout = "mkdocs-material\npymdown-extensions\n")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            assertTrue(result.success)
            assertFalse(result.skipped)
            // Step 1: venv
            assertEquals(listOf("uv", "venv", runtimePath), commands[0])
            // Step 2: install mkdocs base
            assertEquals(listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs"), commands[1])
            // Step 3: get-deps
            assertTrue(commands[2].contains("get-deps"))
            // Step 4: install discovered deps
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs-material", "pymdown-extensions"),
                commands[3],
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `skips dependency install when mkdocs get-deps returns no output`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-no-deps-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 0, stdout = "")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val result = service.bootstrap(projectRoot.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            // Only 3 commands: venv + base install + get-deps (no deps install)
            assertEquals(3, commands.size)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `succeeds even when mkdocs get-deps fails`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-get-deps-fail-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 1, stderr = "bad config")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val result = service.bootstrap(projectRoot.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            // Only 3 commands: venv + base install + get-deps (failed, no deps install)
            assertEquals(3, commands.size)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `includes requirements file in base install command`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-requirements-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val requirementsPath = projectRoot.resolve("requirements.txt")
            requirementsPath.writeText("mkdocs-minify-plugin==0.8.0\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()

            val result = service.bootstrap(projectRoot.toString())
            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(
                listOf(
                    "uv",
                    "pip",
                    "install",
                    "--python",
                    runtimePath,
                    "mkdocs",
                    "-r",
                    requirementsPath.toString(),
                ),
                commands[0],
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `re-runs bootstrap when config file changes`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-config-change-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            val mkdocsConfig = projectRoot.resolve("mkdocs.yml")
            mkdocsConfig.writeText("site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val first = service.bootstrap(projectRoot.toString())
            assertTrue(first.success)
            assertFalse(first.skipped)

            // Second call with same config should skip
            val second = service.bootstrap(projectRoot.toString())
            assertTrue(second.success)
            assertTrue(second.skipped)

            // Change config -> should re-run
            mkdocsConfig.writeText("site_name: Updated Demo\ntheme:\n  name: material\n")
            val third = service.bootstrap(projectRoot.toString())
            assertTrue(third.success)
            assertFalse(third.skipped)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `re-runs bootstrap when requirements file changes`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-req-change-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val requirementsPath = projectRoot.resolve("requirements.txt")
            requirementsPath.writeText("mkdocs-minify-plugin==0.8.0\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }

            val first = service.bootstrap(projectRoot.toString())
            assertTrue(first.success)
            assertFalse(first.skipped)

            val second = service.bootstrap(projectRoot.toString())
            assertTrue(second.success)
            assertTrue(second.skipped)

            // Change requirements -> should re-run
            requirementsPath.writeText("mkdocs-minify-plugin==0.9.0\n")
            val third = service.bootstrap(projectRoot.toString())
            assertTrue(third.success)
            assertFalse(third.skipped)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `skips bootstrap for already prepared project`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-skip-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")

            val calls = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                calls += command
                CommandResult(0)
            }

            service.bootstrap(projectRoot.toString())
            val second = service.bootstrap(projectRoot.toString())

            assertTrue(second.success)
            assertTrue(second.skipped)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
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

    @Test
    fun `returns failure when dependency install fails`() {
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                when {
                    command.contains("get-deps") ->
                        CommandResult(exitCode = 0, stdout = "mkdocs-material\n")
                    command[1] == "pip" && command.contains("mkdocs-material") ->
                        CommandResult(exitCode = 1, stderr = "dependency install failed")
                    else ->
                        CommandResult(exitCode = 0)
                }
            },
            uvExecutableProvider = StaticUvExecutableProvider(),
        )

        val result = service.bootstrap("/tmp/project-fail-deps")

        assertFalse(result.success)
        assertEquals("dependency install failed", result.errorMessage)
    }
}
