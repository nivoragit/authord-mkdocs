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
    ): PluginActivationService {
        return PluginActivationService(
            bootstrapService = bootstrap,
            processManager = processManager,
            baseUrlDetector = BaseUrlDetector(),
            previewPaneCoordinator = PreviewPaneCoordinator(),
            errorPresenter = ActivationErrorPresenter(),
            isDarkIdeTheme = { isDarkIdeTheme },
        )
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
        val svc = service(
            bootstrap = UvBootstrapService { command, _ ->
                if (command[1] == "venv") CommandResult(1, stderr = "boom") else CommandResult(0)
            },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.BOOTSTRAP_FAILED, result.reason)
        assertTrue(result.message.contains("boom"))
    }

    @Test
    fun `fails when process start returns not started`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", false) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.START_FAILED, result.reason)
    }

    @Test
    fun `includes startup output details when process start fails`() {
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
            projectPath = "/tmp/project",
            startupOutput = "",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.START_FAILED, result.reason)
        assertTrue(result.message.contains("Config file 'mkdocs.yml' does not exist"))
    }

    @Test
    fun `includes mkdocs config hint when process start fails without output`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-missing-config-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", false) }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("No mkdocs.yml or mkdocs.yaml found in project root"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails when base url cannot be parsed`() {
        val handle = ActivationHandle("p1", true)
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> handle }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "no url here",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.BASE_URL_NOT_FOUND, result.reason)
        assertEquals(1, handle.stopCalls)
    }

    @Test
    fun `succeeds when bootstrap start and url detection succeed`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "ready at http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertTrue(result.success)
        assertEquals("http://127.0.0.1:8000/", result.previewUrl)
        assertEquals("Activation completed", result.message)
    }

    @Test
    fun `detects preview url from process startup output when explicit startup output is empty`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ ->
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/")
            }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "",
            featureFlags = FeatureFlagPolicy(),
        )

        assertTrue(result.success)
        assertEquals("http://127.0.0.1:8000/", result.previewUrl)
    }

    @Test
    fun `uses parent guard script command for mkdocs runtime start`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-parent-guard-")
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
            assertTrue("--parent-pid" in launcher.command)
            assertTrue("--working-dir" in launcher.command)
            assertTrue("serve" in launcher.command)
            val bindIndex = launcher.command.indexOf("-a")
            assertTrue(bindIndex >= 0)
            val bindAddress = launcher.command.getOrNull(bindIndex + 1).orEmpty()
            assertTrue(bindAddress.startsWith("127.0.0.1:"))
            val boundPort = bindAddress.substringAfter(':', missingDelimiterValue = "-1").toIntOrNull()
            assertTrue(boundPort != null && boundPort > 0)
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = projectRoot.resolve(".authord-mkdocs.theme.yml")
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
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            val expectedInheritPath = projectRoot.resolve("mkdocs.yml").toAbsolutePath().normalize().toString()
            assertTrue(fallbackThemeConfig.contains("INHERIT: '$expectedInheritPath'"))
            assertTrue(fallbackThemeConfig.contains("name: mkdocs"))
            assertTrue(fallbackThemeConfig.contains("color_mode: light"))
            assertTrue(fallbackThemeConfig.contains("user_color_mode_toggle: true"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `adds fallback site_name when mkdocs config omits required key`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-site-name-")
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
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" in launcher.command)
            val fallbackThemePath = projectRoot.resolve(".authord-mkdocs.theme.yml")
            assertTrue(fallbackThemePath.toString() in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            val updatedConfig = Files.readAllLines(projectRoot.resolve("mkdocs.yml"))
            assertTrue(updatedConfig.any { it.trimStart().startsWith("site_name:") })
            val fallbackThemeConfig = Files.readString(fallbackThemePath)
            val expectedInheritPath = projectRoot.resolve("mkdocs.yml").toAbsolutePath().normalize().toString()
            assertTrue(fallbackThemeConfig.contains("INHERIT: '$expectedInheritPath'"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not use fallback theme config when mkdocs config defines theme`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-theme-present-")
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
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" !in launcher.command)
            val fallbackThemePath = projectRoot.resolve(".authord-mkdocs.theme.yml")
            assertTrue(fallbackThemePath.toString() !in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(!Files.exists(fallbackThemePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `does not use fallback theme config when theme key is indented`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-indented-theme-present-")
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
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertTrue("-f" !in launcher.command)
            val fallbackThemePath = projectRoot.resolve(".authord-mkdocs.theme.yml")
            assertTrue(fallbackThemePath.toString() !in launcher.command)
            assertTrue("--theme" !in launcher.command)
            assertTrue("theme.color_mode=auto" !in launcher.command)
            assertTrue("theme.user_color_mode_toggle=true" !in launcher.command)
            assertTrue(!Files.exists(fallbackThemePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
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
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            val fallbackThemeConfig = Files.readString(
                projectRoot.resolve(".authord-mkdocs.theme.yml"),
            )
            assertTrue(fallbackThemeConfig.contains("name: mkdocs"))
            assertTrue(fallbackThemeConfig.contains("color_mode: dark"))
            assertTrue(!fallbackThemeConfig.contains("color_mode: light"))
            assertTrue(fallbackThemeConfig.contains("user_color_mode_toggle: true"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
