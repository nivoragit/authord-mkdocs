package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.runtime.StaticUvExecutableProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ActivationHandle(
    override val id: String,
    private var alive: Boolean,
    private val startupOutputText: String = "",
    private val stdoutText: String = startupOutputText,
    private val stderrText: String = "",
    private val exitCode: Int? = null,
) : ManagedProcessHandle {
    var stopCalls: Int = 0
        private set

    override fun stop() {
        stopCalls += 1
        alive = false
    }

    override fun isAlive(): Boolean = alive

    override fun startupOutput(): String = startupOutputText

    override fun stdoutOutput(): String = stdoutText

    override fun stderrOutput(): String = stderrText

    override fun exitCodeOrNull(): Int? = if (alive) null else exitCode
}

class PluginActivationServiceTest {
    private class RecordingStartLauncher(
        private val handle: ManagedProcessHandle,
    ) : ProcessLauncher {
        var command: List<String> = emptyList()
            private set
        var workingDir: String = ""
            private set

        override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
            this.command = command
            this.workingDir = workingDir
            return handle
        }
    }

    private fun service(
        bootstrap: UvBootstrapService,
        processManager: MkdocsProcessManager,
        isDarkIdeTheme: Boolean = false,
        readinessProbe: HttpReadinessProbe = HttpReadinessProbe { true },
        maxStartupAttempts: Int = 5,
        startupProbeTimeoutMillis: Long = 5_000L,
        startupPollIntervalMillis: Long = 150L,
        nowMillisProvider: () -> Long = System::currentTimeMillis,
        sleeper: (Long) -> Unit = { },
        pluginEnvironmentRoot: Path? = null,
    ): PluginActivationService {
        return PluginActivationService(
            bootstrapService = bootstrap,
            processManager = processManager,
            baseUrlDetector = BaseUrlDetector(),
            previewPaneCoordinator = PreviewPaneCoordinator(),
            errorPresenter = ActivationErrorPresenter(),
            isDarkIdeTheme = { isDarkIdeTheme },
            readinessProbe = readinessProbe,
            maxStartupAttempts = maxStartupAttempts,
            startupProbeTimeoutMillis = startupProbeTimeoutMillis,
            startupPollIntervalMillis = startupPollIntervalMillis,
            nowMillisProvider = nowMillisProvider,
            sleeper = sleeper,
            pluginEnvironmentRootProvider = {
                pluginEnvironmentRoot ?: Path.of(System.getProperty("java.io.tmpdir")).resolve("authord-plugin-test-env")
            },
        )
    }

    private fun expectedFallbackThemePath(pluginEnvironmentRoot: Path, projectId: String, projectPath: String): Path {
        val normalizedProjectPath = Path.of(projectPath).toAbsolutePath().normalize().toString()
        val pathFingerprint = normalizedProjectPath.hashCode().toUInt().toString(16)
        val safeProjectId = projectId.ifBlank { "default" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return pluginEnvironmentRoot
            .resolve("theme")
            .resolve("${safeProjectId}_$pathFingerprint")
            .resolve(".authord.theme.yml")
    }

    private fun createProjectRootWithConfig(prefix: String): Path {
        val projectRoot = createTempDirectory(prefix = prefix)
        Files.writeString(
            projectRoot.resolve("mkdocs.yml"),
            """
                site_name: Demo
                docs_dir: docs
                nav: []
            """.trimIndent() + "\n",
        )
        return projectRoot
    }

    @Test
    fun `fails when mvp flow is disabled`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(mvpEnabled = false),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.MVP_DISABLED, result.reason)
    }

    @Test
    fun `fails when bootstrap fails`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-bootstrap-fail-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { command, _ ->
                    if (command[1] == "venv") CommandResult(1, stderr = "boom") else CommandResult(0)
                },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "http://127.0.0.1:8000/",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.BOOTSTRAP_FAILED, result.reason)
            assertTrue(result.message.contains("boom"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails when process start returns not started`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-start-fail-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", false) }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "http://127.0.0.1:8000/",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `includes startup output details when process start fails`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-startup-output-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ ->
                    ActivationHandle(
                        id = "p1",
                        alive = false,
                        startupOutputText = "Error: Config file 'mkdocs.yml' does not exist.",
                    )
                }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("Config file 'mkdocs.yml' does not exist"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails fast with mkdocs config hint when config is missing`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-missing-config-")
        try {
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = false, startupOutputText = "should-not-run"),
            )
            var bootstrapCalls = 0
            val svc = service(
                bootstrap = UvBootstrapService { _, _ ->
                    bootstrapCalls += 1
                    CommandResult(0)
                },
                processManager = MkdocsProcessManager(launcher),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("No configuration file found in project root"))
            assertEquals(0, bootstrapCalls)
            assertTrue(launcher.command.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails when readiness probe does not succeed and retries stop the process`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-readiness-timeout-")
        try {
            val handle = ActivationHandle("p1", true)
            var now = 0L
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> handle }),
                readinessProbe = HttpReadinessProbe { false },
                maxStartupAttempts = 2,
                startupProbeTimeoutMillis = 1L,
                startupPollIntervalMillis = 1L,
                nowMillisProvider = { now++ },
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "no url here",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertEquals(2, handle.stopCalls)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `process exit before readiness reports actionable dependency guidance`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-dep-guidance-")
        try {
            val handle = object : ManagedProcessHandle {
                override val id: String = "dep-failure-handle"
                private var aliveChecks = 0
                private var stopped = false

                override fun stop() {
                    stopped = true
                }

                override fun isAlive(): Boolean {
                    if (stopped) {
                        return false
                    }
                    aliveChecks += 1
                    return aliveChecks == 1
                }

                override fun startupOutput(): String {
                    return "Authord process exited before readiness probe succeeded."
                }

                override fun stdoutOutput(): String = ""

                override fun stderrOutput(): String {
                    return "ModuleNotFoundError: No module named 'material'\\nmaterial.extensions.emoji"
                }

                override fun exitCodeOrNull(): Int? = 1
            }

            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> handle }),
                readinessProbe = HttpReadinessProbe { false },
                maxStartupAttempts = 1,
                startupProbeTimeoutMillis = 1_000L,
                startupPollIntervalMillis = 1L,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("mkdocs-material"))
            assertTrue(result.message.contains("-m pip install mkdocs-material"))
            assertTrue(result.message.contains("requirements.txt / requirements-docs.txt / pyproject.toml"))
            assertTrue(result.message.contains("Retry dependency setup"))
            assertEquals("mkdocs-material", result.diagnostics.suggestedPackage)
            assertTrue(result.diagnostics.pythonExecutable.contains(".mkdocs-plugin-venv"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `succeeds when bootstrap start and readiness probe succeeds`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-success-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "ready at http://127.0.0.1:8000/",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue(result.previewUrl.startsWith("http://127.0.0.1:"))
            assertTrue(result.previewUrl.endsWith("/"))
            assertEquals("Activation completed", result.message)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `startup output is not required when readiness probe succeeds`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-no-startup-output-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ ->
                    ActivationHandle("p1", alive = true, startupOutputText = "WARNING: unrelated URL https://example.com/docs")
                }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue(result.previewUrl.startsWith("http://127.0.0.1:"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uses parent guard script command for mkdocs runtime start`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-parent-guard-")
        val pluginEnvRoot = createTempDirectory(prefix = "authord-plugin-env-parent-guard-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
                pluginEnvironmentRoot = pluginEnvRoot,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertEquals(projectRoot.toString(), launcher.workingDir)
            val expectedRuntimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()
            assertTrue(
                launcher.command.take(5) == listOf(
                    "/tmp/custom-uv",
                    "run",
                    "--python",
                    expectedRuntimePath,
                    "python",
                ),
            )
            val runtimePython = projectRoot.resolve(".mkdocs-plugin-venv").resolve("bin").resolve("python").toString()
            val runtimePythonIndex = launcher.command.indexOf(runtimePython)
            assertTrue(runtimePythonIndex > 0)
            assertEquals("-m", launcher.command.getOrNull(runtimePythonIndex + 1))
            assertEquals("mkdocs", launcher.command.getOrNull(runtimePythonIndex + 2))
            assertEquals("serve", launcher.command.getOrNull(runtimePythonIndex + 3))
            assertTrue("--parent-pid" in launcher.command)
            assertTrue("--working-dir" in launcher.command)
            assertTrue("serve" in launcher.command)
            val bindIndex = launcher.command.indexOf("--dev-addr")
            assertTrue(bindIndex >= 0)
            val bindAddress = launcher.command.getOrNull(bindIndex + 1).orEmpty()
            assertTrue(bindAddress.startsWith("127.0.0.1:"))
            val boundPort = bindAddress.substringAfter(':', missingDelimiterValue = "-1").toIntOrNull()
            assertTrue(boundPort != null && boundPort > 0)
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("mkdocs" in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue("--livereload" in launcher.command)
            assertTrue("--dirty" in launcher.command)
            assertTrue("--dirtyreload" !in launcher.command)

            val scriptPath = projectRoot.resolve(".mkdocs-plugin-runtime").resolve("serve_with_parent_guard.py")
            assertTrue(Files.exists(scriptPath))
            val script = Files.readString(scriptPath)
            assertTrue(script.contains("def parent_alive"))
            assertTrue(script.contains("if not parent_alive"))

            assertTrue(Files.exists(fallbackThemePath))
            assertTrue(!Files.exists(projectRoot.resolve(".authord.theme.yml")))
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            val expectedInheritPath = projectRoot.resolve("mkdocs.yml").toAbsolutePath().normalize().toString()
            val expectedDocsDirPath = projectRoot.resolve("docs").toAbsolutePath().normalize().toString()
            assertTrue(fallbackThemeConfig.contains("INHERIT: '$expectedInheritPath'"))
            assertTrue(fallbackThemeConfig.contains("docs_dir: '$expectedDocsDirPath'"))
            assertTrue(fallbackThemeConfig.contains("name: mkdocs"))
            assertTrue(fallbackThemeConfig.contains("color_mode: light"))
            assertTrue(fallbackThemeConfig.contains("user_color_mode_toggle: true"))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `adds fallback site_name when mkdocs config omits required key`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-site-name-")
        val pluginEnvRoot = createTempDirectory(prefix = "authord-plugin-env-site-name-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
                pluginEnvironmentRoot = pluginEnvRoot,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() in launcher.command)
            assertTrue(!Files.exists(projectRoot.resolve(".authord.theme.yml")))
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            val updatedConfig = Files.readAllLines(projectRoot.resolve("mkdocs.yml"))
            assertTrue(updatedConfig.any { it.trimStart().startsWith("site_name:") })
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            val expectedInheritPath = projectRoot.resolve("mkdocs.yml").toAbsolutePath().normalize().toString()
            val expectedDocsDirPath = projectRoot.resolve("docs").toAbsolutePath().normalize().toString()
            assertTrue(fallbackThemeConfig.contains("INHERIT: '$expectedInheritPath'"))
            assertTrue(fallbackThemeConfig.contains("docs_dir: '$expectedDocsDirPath'"))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not use fallback theme config when mkdocs config defines theme`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-theme-present-")
        val pluginEnvRoot = createTempDirectory(prefix = "authord-plugin-env-theme-present-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    docs_dir: docs
                    theme:
                      name: material
                    nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
                pluginEnvironmentRoot = pluginEnvRoot,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" !in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() !in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(!Files.exists(fallbackThemePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not use fallback theme config when theme key is indented`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-indented-theme-present-")
        val pluginEnvRoot = createTempDirectory(prefix = "authord-plugin-env-indented-theme-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                      docs_dir: docs
                      theme:
                        name: material
                      nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
                pluginEnvironmentRoot = pluginEnvRoot,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" !in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() !in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(!Files.exists(fallbackThemePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preserves existing site_name without duplicating key`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-existing-site-name-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Existing Name
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            val lines = Files.readAllLines(projectRoot.resolve("mkdocs.yml"))
            val siteNameCount = lines.count { it.trimStart().startsWith("site_name:") }
            assertEquals(1, siteNameCount)
            assertTrue(lines.any { it.contains("site_name: Existing Name") })
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `writes dark color mode to fallback theme config when ide theme is dark`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-dark-theme-")
        val pluginEnvRoot = createTempDirectory(prefix = "authord-plugin-env-dark-theme-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService(
                    commandRunner = { _, _ -> CommandResult(0) },
                    uvExecutableProvider = StaticUvExecutableProvider("/tmp/custom-uv"),
                ),
                processManager = MkdocsProcessManager(launcher),
                isDarkIdeTheme = true,
                pluginEnvironmentRoot = pluginEnvRoot,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            val fallbackThemeConfig = Files.readString(
                expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString()),
            )
            assertTrue(fallbackThemeConfig.contains("name: mkdocs"))
            assertTrue(fallbackThemeConfig.contains("color_mode: dark"))
            assertTrue(!fallbackThemeConfig.contains("color_mode: light"))
            assertTrue(fallbackThemeConfig.contains("user_color_mode_toggle: true"))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }
}
