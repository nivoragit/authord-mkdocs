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
        val projectPath = "/tmp/project"
        val expectedRuntimePath = runtimePath(projectPath)
        val expectedPython = runtimePython(projectPath)
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            },
            uvExecutableProvider = StaticUvExecutableProvider(),
        )

        val result = service.bootstrap(projectPath)

        assertTrue(result.success)
        assertFalse(result.skipped)
        assertEquals("uv", result.uvExecutablePath)
        assertEquals(listOf("uv", "venv", expectedRuntimePath), commands[0])
        assertEquals(listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"), commands[1])
        assertEquals(listOf(expectedPython, "-m", "mkdocs", "get-deps"), commands[2])
        assertTrue(commands.contains(listOf(expectedPython, "-m", "mkdocs", "build", "--strict")))
        assertEquals(expectedRuntimePath, result.runtimePath)
        assertEquals(expectedPython, result.diagnostics.pythonExecutable)
        assertEquals(BootstrapVerificationMode.STRICT, result.diagnostics.verificationMode)
    }

    @Test
    fun `uses resolved uv executable path for all commands`() {
        val commands = mutableListOf<List<String>>()
        val projectPath = "/tmp/custom-uv-project"
        val customUv = "/tmp/authord-runtime-tools/uv"
        val expectedRuntimePath = runtimePath(projectPath)
        val expectedPython = runtimePython(projectPath)
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
        assertEquals(listOf(customUv, "pip", "install", "--python", expectedPython, "mkdocs"), commands[1])
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
        assertEquals(BootstrapFailureCategory.UV_RESOLUTION, result.failureCategory)
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
            val expectedPython = runtimePython(tempProject.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"), commands[0])
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
        val expectedRuntimePath = runtimePath(projectPath)
        val expectedPython = runtimePython(projectPath)
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
        assertEquals(listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"), commands[1])
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
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 0, stdout = "mkdocs-material\npymdown-extensions\n")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val result = service.bootstrap(projectRoot.toString())
            val runtimePath = runtimePath(projectRoot.toString())
            val expectedPython = runtimePython(projectRoot.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            assertEquals(listOf("uv", "venv", runtimePath), commands[0])
            assertEquals(listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"), commands[1])
            assertTrue(commands[2].contains("get-deps"))
            assertTrue(
                commands.contains(
                    listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs-material", "pymdown-extensions"),
                ),
            )
            assertEquals(DependencyBootstrapOutcome.SUCCESS, result.diagnostics.getDepsOutcome)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails when mkdocs get-deps fails and marks start anyway availability`() {
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

            assertFalse(result.success)
            assertFalse(result.skipped)
            assertEquals(BootstrapFailureCategory.GET_DEPS, result.failureCategory)
            assertTrue(result.canStartAnyway)
            assertTrue(result.errorMessage.contains("bad config"))
            assertEquals(3, result.executedCommands.size)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `bootstrap succeeds after adding mkdocs material to requirements`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-material-requirements-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText(
                """
                site_name: Demo
                markdown_extensions:
                  - material.extensions.emoji
                """.trimIndent() + "\n",
            )

            var materialInstalledInRuntime = false
            val service = UvBootstrapService { command, _ ->
                when {
                    command.size > 1 && command[1] == "pip" -> {
                        val requirementFlagIndex = command.indexOf("-r")
                        if (requirementFlagIndex >= 0) {
                            val requirementsPath = Path.of(command[requirementFlagIndex + 1])
                            val requirementsContent = requirementsPath.toFile().takeIf { it.exists() }?.readText().orEmpty()
                            materialInstalledInRuntime = requirementsContent.contains("mkdocs-material")
                        }
                        CommandResult(exitCode = 0)
                    }
                    command.contains("get-deps") -> {
                        if (materialInstalledInRuntime) {
                            CommandResult(exitCode = 0, stdout = "mkdocs-material\n")
                        } else {
                            CommandResult(
                                exitCode = 1,
                                stderr = "ModuleNotFoundError: No module named 'material'",
                            )
                        }
                    }
                    else -> CommandResult(exitCode = 0)
                }
            }

            val first = service.bootstrap(projectRoot.toString())
            assertFalse(first.success)
            assertEquals(BootstrapFailureCategory.GET_DEPS, first.failureCategory)

            projectRoot.resolve("requirements.txt").writeText("mkdocs-material\n")
            materialInstalledInRuntime = false

            val second = service.bootstrap(projectRoot.toString())
            assertTrue(second.success)
            assertFalse(second.skipped)
            assertEquals(DependencyBootstrapOutcome.SUCCESS, second.diagnostics.getDepsOutcome)
            val baseInstall = second.executedCommands.firstOrNull { command ->
                command.size > 1 && command[1] == "pip" && command.contains("mkdocs")
            }
            assertTrue(baseInstall != null)
            assertTrue(baseInstall.contains("-r"))
            assertTrue(baseInstall.any { it.endsWith("requirements.txt") })
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `start anyway bypass allows bootstrap to continue after get deps failure`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-start-anyway-")
        try {
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val service = UvBootstrapService { command, _ ->
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 1, stderr = "bad config")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val failed = service.bootstrap(projectRoot.toString())
            assertFalse(failed.success)
            assertTrue(failed.canStartAnyway)

            service.requestStartAnyway(projectRoot.toString())
            val bypassed = service.bootstrap(projectRoot.toString())

            assertTrue(bypassed.success)
            assertEquals(DependencyBootstrapOutcome.BYPASSED, bypassed.diagnostics.getDepsOutcome)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `includes requirements txt files in base install command`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-requirements-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val requirementsPath = projectRoot.resolve("requirements.txt")
            requirementsPath.writeText("mkdocs-minify-plugin==0.8.0\n")
            val docsRequirementsPath = projectRoot.resolve("requirements-docs.txt")
            docsRequirementsPath.writeText("mkdocs-material==9.5.0\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val expectedPython = runtimePython(projectRoot.toString())

            val result = service.bootstrap(projectRoot.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            val baseInstall = commands[0]
            assertEquals(
                listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"),
                baseInstall.take(6),
            )
            val requirementPairs = baseInstall.drop(6).chunked(2)
            assertEquals(
                setOf(requirementsPath.toString(), docsRequirementsPath.toString()),
                requirementPairs.mapNotNull { pair -> pair.getOrNull(1) }.toSet(),
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `includes only non empty requirements txt files in base install command`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-requirements-non-empty-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val emptyRequirementsPath = projectRoot.resolve("requirements.txt")
            emptyRequirementsPath.writeText("")
            val docsRequirementsPath = projectRoot.resolve("requirements-docs.txt")
            docsRequirementsPath.writeText("mkdocs-material==9.5.0\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val expectedPython = runtimePython(projectRoot.toString())

            val result = service.bootstrap(projectRoot.toString())

            assertTrue(result.success)
            assertFalse(result.skipped)
            val baseInstall = commands[0]
            assertEquals(
                listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"),
                baseInstall.take(6),
            )
            val requirementPairs = baseInstall.drop(6).chunked(2)
            assertEquals(
                setOf(docsRequirementsPath.toString()),
                requirementPairs.mapNotNull { pair -> pair.getOrNull(1) }.toSet(),
            )
            assertTrue(!baseInstall.contains(emptyRequirementsPath.toString()))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scopes dependency discovery and requirements install to discovered mkdocs config directory`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-scoped-config-")
        try {
            val docsProject = projectRoot.resolve("docs-site")
            docsProject.createDirectories()
            docsProject.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            docsProject.resolve("requirements.txt").writeText("mkdocs-material==9.5.0\n")
            projectRoot.resolve("requirements.txt").writeText("should-not-be-used==1.0.0\n")

            val invocations = mutableListOf<Pair<List<String>, String>>()
            val service = UvBootstrapService { command, workingDir ->
                invocations += command to workingDir
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 0, stdout = "mkdocs-material\n")
                } else {
                    CommandResult(exitCode = 0)
                }
            }

            val result = service.bootstrap(projectRoot.toString())
            val expectedPython = runtimePython(projectRoot.toString())
            val scopedRequirements = docsProject.resolve("requirements.txt").toString()
            val rootRequirements = projectRoot.resolve("requirements.txt").toString()

            assertTrue(result.success)
            val baseInstall = invocations
                .map { it.first }
                .first { command -> command.size > 1 && command[1] == "pip" && command.contains("mkdocs") }
            assertTrue(baseInstall.contains("-r"))
            assertTrue(baseInstall.contains(scopedRequirements))
            assertTrue(!baseInstall.contains(rootRequirements))
            assertEquals(listOf("uv", "pip", "install", "--python", expectedPython, "mkdocs"), baseInstall.take(6))

            val getDepsInvocation = invocations.first { (command, _) -> command.contains("get-deps") }
            assertEquals(docsProject.toString(), getDepsInvocation.second)
            assertTrue(getDepsInvocation.first.contains("-f"))
            assertTrue(getDepsInvocation.first.contains(docsProject.resolve("mkdocs.yml").toString()))

            val strictInvocation = invocations.first { (command, _) ->
                command.size >= 4 && command[1] == "-m" && command[2] == "mkdocs" && command.contains("--strict")
            }
            assertEquals(docsProject.toString(), strictInvocation.second)
            assertTrue(strictInvocation.first.contains("-f"))
            assertTrue(strictInvocation.first.contains(docsProject.resolve("mkdocs.yml").toString()))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `re-runs bootstrap when pyproject changes`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-pyproject-change-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
            val pyproject = projectRoot.resolve("pyproject.toml")
            pyproject.writeText("[project]\nname='demo'\n")

            val service = UvBootstrapService { _, _ -> CommandResult(0) }

            val first = service.bootstrap(projectRoot.toString())
            assertTrue(first.success)
            assertFalse(first.skipped)

            val second = service.bootstrap(projectRoot.toString())
            assertTrue(second.success)
            assertTrue(second.skipped)

            pyproject.writeText("[project]\nname='demo'\nversion='0.2.0'\n")
            val third = service.bootstrap(projectRoot.toString())
            assertTrue(third.success)
            assertFalse(third.skipped)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `strict verification is skipped on unchanged fingerprint and can be requested explicitly`() {
        val projectRoot = createTempDirectory(prefix = "uv-bootstrap-strict-gating-")
        try {
            projectRoot.resolve(".mkdocs-plugin-venv").createDirectories()
            projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(0)
            }

            val first = service.bootstrap(projectRoot.toString())
            assertTrue(first.success)
            assertTrue(commands.any(::isStrictCommand))

            commands.clear()
            val second = service.bootstrap(projectRoot.toString())
            assertTrue(second.success)
            assertTrue(second.skipped)
            assertFalse(commands.any(::isStrictCommand))
            assertTrue(commands.any(::isFastMkdocsVersionCommand))

            commands.clear()
            service.requestStrictVerification(projectRoot.toString())
            val third = service.bootstrap(projectRoot.toString())
            assertTrue(third.success)
            assertTrue(third.skipped)
            assertTrue(commands.any(::isStrictCommand))
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
        assertEquals(BootstrapFailureCategory.VENV_SETUP, result.failureCategory)
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
        assertEquals(BootstrapFailureCategory.BASE_INSTALL, result.failureCategory)
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
        assertEquals(BootstrapFailureCategory.DEPENDENCY_INSTALL, result.failureCategory)
    }

    @Test
    fun `returns fast verification failure when mkdocs version check fails`() {
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                if (command.size >= 4 && command[1] == "-m" && command[2] == "mkdocs" && command[3] == "--version") {
                    CommandResult(exitCode = 1, stderr = "mkdocs version probe failed")
                } else {
                    CommandResult(exitCode = 0)
                }
            },
            uvExecutableProvider = StaticUvExecutableProvider(),
        )

        val result = service.bootstrap("/tmp/project-fast-verify-fail")

        assertFalse(result.success)
        assertEquals(BootstrapFailureCategory.FAST_VERIFY, result.failureCategory)
        assertTrue(result.errorMessage.contains("mkdocs version probe failed"))
        assertEquals(BootstrapVerificationMode.FAST, result.diagnostics.verificationMode)
        assertEquals(false, result.diagnostics.verificationSucceeded)
    }

    @Test
    fun `returns strict verification failure and allows start anyway`() {
        val service = UvBootstrapService(
            commandRunner = { command, _ ->
                if (isStrictCommand(command)) {
                    CommandResult(exitCode = 1, stderr = "strict build failed")
                } else {
                    CommandResult(exitCode = 0)
                }
            },
            uvExecutableProvider = StaticUvExecutableProvider(),
        )

        val result = service.bootstrap("/tmp/project-strict-verify-fail")

        assertFalse(result.success)
        assertEquals(BootstrapFailureCategory.STRICT_VERIFY, result.failureCategory)
        assertTrue(result.canStartAnyway)
        assertTrue(result.errorMessage.contains("strict build failed"))
        assertEquals(BootstrapVerificationMode.STRICT, result.diagnostics.verificationMode)
        assertEquals(false, result.diagnostics.verificationSucceeded)
    }

    @Test
    fun `diagnostics include uv descriptor metadata when uv path is a real file`() {
        val tempDir = createTempDirectory(prefix = "uv-bootstrap-uv-descriptor-")
        try {
            val fakeUv = tempDir.resolve("uv")
            fakeUv.writeText("uv-binary")
            val service = UvBootstrapService(
                commandRunner = { _, _ -> CommandResult(exitCode = 0) },
                uvExecutableProvider = StaticUvExecutableProvider(fakeUv.toString()),
            )

            val result = service.bootstrap(tempDir.toString())

            assertTrue(result.success)
            assertTrue(result.diagnostics.uvDescriptor.contains("size="))
            assertTrue(result.diagnostics.uvDescriptor.contains("mtime="))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun runtimePath(projectPath: String): String {
        return Path.of(projectPath).resolve(".mkdocs-plugin-venv").toString()
    }

    private fun runtimePython(projectPath: String): String {
        return Path.of(projectPath).resolve(".mkdocs-plugin-venv").resolve("bin").resolve("python").toString()
    }

    private fun isStrictCommand(command: List<String>): Boolean {
        return command.size >= 5 &&
            command[1] == "-m" &&
            command[2] == "mkdocs" &&
            command[3] == "build" &&
            command[4] == "--strict"
    }

    private fun isFastMkdocsVersionCommand(command: List<String>): Boolean {
        return command.size >= 4 &&
            command[1] == "-m" &&
            command[2] == "mkdocs" &&
            command[3] == "--version"
    }
}
