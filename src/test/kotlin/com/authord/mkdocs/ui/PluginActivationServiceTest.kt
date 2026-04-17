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
) : ManagedProcessHandle {
    var stopCalls: Int = 0
        private set

    override fun stop() {
        stopCalls += 1
        alive = false
    }

    override fun isAlive(): Boolean = alive

    override fun startupOutput(): String = startupOutputText
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
    fun `prompts explicit bootstrap fallback when no runtime command is runnable`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-no-runtime-")
        try {
            var bootstrapCalls = 0
            val svc = service(
                bootstrap = UvBootstrapService { _, _ ->
                    bootstrapCalls += 1
                    CommandResult(0)
                },
                processManager = MkdocsProcessManager(ProcessLauncher { command, _ ->
                    val binary = command.firstOrNull().orEmpty()
                    throw IllegalStateException("Cannot run program \"$binary\": error=2, No such file or directory")
                }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "http://127.0.0.1:8000/",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("No runnable MkDocs environment detected"))
            assertEquals(0, bootstrapCalls)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `falls back to uv run after mkdocs poetry and pipenv are unavailable`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-uv-fallback-")
        try {
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val processManager = MkdocsProcessManager(
                ProcessLauncher { command, workingDir ->
                    val binary = command.firstOrNull().orEmpty()
                    val binaryName = Path.of(binary).fileName?.toString()?.lowercase().orEmpty()
                    val unavailable = (binaryName == "mkdocs" || binaryName == "mkdocs.exe") ||
                        (binaryName == "poetry" && command.getOrNull(1) == "run") ||
                        (binaryName == "pipenv" && command.getOrNull(1) == "run")
                    if (unavailable) {
                        throw IllegalStateException("Cannot run program \"$binary\": error=2, No such file or directory")
                    }
                    launcher.launch(command, workingDir)
                },
            )
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = processManager,
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue(launcher.command.size >= 3)
            assertTrue(launcher.command[0].endsWith("uv") || launcher.command[0].endsWith("uv.exe"))
            assertTrue(launcher.command[1] == "run")
            assertTrue(launcher.command[2] == "mkdocs")
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
    fun `uses direct mkdocs runtime command for runtime start`() {
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
            val authordVenvMkdocs = projectRoot.resolve(".authord_venv").resolve("bin").resolve("mkdocs")
            Files.createDirectories(authordVenvMkdocs.parent)
            Files.writeString(authordVenvMkdocs, "#!/bin/sh\n")
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
            assertTrue(
                launcher.command.take(2) == listOf(
                    authordVenvMkdocs.toString(),
                    "serve",
                ),
            )
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
            assertTrue(launcher.command.any { it.endsWith("mkdocs") || it.endsWith("mkdocs.exe") })
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue("--livereload" in launcher.command)
            assertTrue("--dirty" in launcher.command)
            assertTrue("--dirtyreload" !in launcher.command)

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
    fun `adds fallback site_name in plugin scoped config when mkdocs config omits key`() {
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
            assertTrue(updatedConfig.none { it.trimStart().startsWith("site_name:") })
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            val expectedInheritPath = projectRoot.resolve("mkdocs.yml").toAbsolutePath().normalize().toString()
            val expectedDocsDirPath = projectRoot.resolve("docs").toAbsolutePath().normalize().toString()
            assertTrue(fallbackThemeConfig.contains("INHERIT: '$expectedInheritPath'"))
            assertTrue(fallbackThemeConfig.contains("docs_dir: '$expectedDocsDirPath'"))
            assertTrue(fallbackThemeConfig.contains("site_name:"))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uses plugin scoped config for missing site_name even when theme is defined`() {
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
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(Files.exists(fallbackThemePath))
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            assertTrue(fallbackThemeConfig.contains("site_name:"))
            assertTrue(!fallbackThemeConfig.contains("name: mkdocs"))
        } finally {
            projectRoot.toFile().deleteRecursively()
            pluginEnvRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uses plugin scoped config for missing site_name when theme key is indented`() {
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
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = expectedFallbackThemePath(pluginEnvRoot, "project-1", projectRoot.toString())
            assertTrue(fallbackThemePath.toString() in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(Files.exists(fallbackThemePath))
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            assertTrue(fallbackThemeConfig.contains("site_name:"))
            assertTrue(!fallbackThemeConfig.contains("name: mkdocs"))
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

    @Test
    fun `activates when only nested mkdocs config exists`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-nested-config-")
        try {
            val nestedSiteRoot = Files.createDirectories(projectRoot.resolve("sites").resolve("nested"))
            val nestedConfig = nestedSiteRoot.resolve("mkdocs.yml")
            Files.writeString(
                nestedConfig,
                """
                    site_name: Nested
                    docs_dir: docs
                    theme:
                      name: material
                """.trimIndent() + "\n",
            )
            Files.createDirectories(nestedSiteRoot.resolve("docs"))

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
            assertTrue("-f" in launcher.command)
            assertTrue(nestedConfig.toString() in launcher.command)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `prefers site local venv when mkdocs config is nested under project root`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-nested-site-venv-")
        try {
            val nestedSiteRoot = Files.createDirectories(projectRoot.resolve("docs"))
            val nestedConfig = nestedSiteRoot.resolve("mkdocs.yml")
            Files.writeString(
                nestedConfig,
                """
                    site_name: Nested
                    theme:
                      name: material
                """.trimIndent() + "\n",
            )
            Files.createDirectories(nestedSiteRoot.resolve("docs"))

            val siteVenvMkdocs = nestedSiteRoot.resolve(".venv").resolve("bin").resolve("mkdocs")
            Files.createDirectories(siteVenvMkdocs.parent)
            Files.writeString(siteVenvMkdocs, "#!/bin/sh\n")

            val projectRuntimeMkdocs = projectRoot.resolve(".authord_venv").resolve("bin").resolve("mkdocs")
            Files.createDirectories(projectRuntimeMkdocs.parent)
            Files.writeString(projectRuntimeMkdocs, "#!/bin/sh\n")

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
            assertEquals(siteVenvMkdocs.toString(), launcher.command.firstOrNull())
            assertTrue("-f" in launcher.command)
            assertTrue(nestedConfig.toString() in launcher.command)
            assertTrue(projectRuntimeMkdocs.toString() !in launcher.command)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not mark startup ready when probe succeeds after process exit`() {
        val projectRoot = createProjectRootWithConfig(prefix = "plugin-activation-probe-race-")
        try {
            var alive = true
            var stopCalls = 0
            val handle = object : ManagedProcessHandle {
                override val id: String = "probe-race"

                override fun stop() {
                    stopCalls += 1
                    alive = false
                }

                override fun isAlive(): Boolean = alive
            }

            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> handle }),
                readinessProbe = HttpReadinessProbe {
                    alive = false
                    true
                },
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
            assertTrue(stopCalls >= 1)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
