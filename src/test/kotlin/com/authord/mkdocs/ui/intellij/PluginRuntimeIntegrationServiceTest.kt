package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
import com.authord.mkdocs.ui.ActivationErrorPresenter
import com.authord.mkdocs.ui.FeatureFlagPolicyService
import com.authord.mkdocs.ui.NavigationCoordinator
import com.authord.mkdocs.ui.PluginActivationService
import com.authord.mkdocs.ui.PreviewNavigationFailureHandler
import com.authord.mkdocs.ui.PreviewPaneCoordinator
import com.authord.mkdocs.core.navigation.RouteMappingService
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class LifecycleHandle(
    override val id: String,
) : ManagedProcessHandle {
    private var alive: Boolean = true

    override fun stop() {
        alive = false
    }

    override fun isAlive(): Boolean = alive
}

private class CountingProcessLauncher : ProcessLauncher {
    private val counter = AtomicInteger(0)
    var launchCount: Int = 0
        private set
    var lastCommand: List<String> = emptyList()
        private set

    override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
        launchCount += 1
        lastCommand = command
        return LifecycleHandle("process-${counter.incrementAndGet()}")
    }
}

private class SuccessCommandRunner : CommandRunner {
    override fun run(command: List<String>, workingDir: String): CommandResult = CommandResult(exitCode = 0)
}

private class MutationReloadRecordingPreviewContent : PreviewContent {
    override val component = javax.swing.JPanel()
    val loadedUrls: MutableList<String> = mutableListOf()

    override fun loadUrl(url: String) {
        loadedUrls += url
    }
}

class PluginRuntimeIntegrationServiceTest {
    @Test
    fun `startPreview fails when project path is unavailable`() {
        val project = IntellijTestFixtures.project(basePath = null)
        val service = PluginRuntimeIntegrationService(project)

        val result = service.startPreview()

        assertFalse(result.success)
        assertFalse(service.isRuntimeRunning())
    }

    @Test
    fun `runtime integration preserves single-instance lifecycle through process manager`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val launcher = CountingProcessLauncher()
        val processManager = MkdocsProcessManager(launcher)
        val previewPane = PreviewPaneCoordinator()

        val dependencies = RuntimeIntegrationDependencies(
            activationService = PluginActivationService(
                bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                processManager = processManager,
                baseUrlDetector = BaseUrlDetector(),
                previewPaneCoordinator = previewPane,
                errorPresenter = ActivationErrorPresenter(),
                readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
            ),
            processManager = processManager,
            previewPaneCoordinator = previewPane,
            navigationCoordinator = NavigationCoordinator(
                routeMappingService = RouteMappingService(),
                previewPaneCoordinator = previewPane,
                failureHandler = PreviewNavigationFailureHandler(),
            ),
            featureFlagPolicyService = FeatureFlagPolicyService(),
            startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
        )

        val service = PluginRuntimeIntegrationService(project)
        service.overrideDependenciesForTesting(dependencies)

        val first = service.startPreview()
        val second = service.startPreview()

        assertTrue(first.success)
        assertTrue(second.success)
        assertEquals(1, launcher.launchCount)
        assertTrue(service.isRuntimeRunning())
        assertTrue(service.canStartPreview())
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))

        assertTrue(service.stopPreview())
        assertFalse(service.isRuntimeRunning())
    }

    @Test
    fun `startPreview does not restart running runtime before first config fingerprint is known`() {
        val projectRoot = createTempDirectory(prefix = "runtime-running-no-fingerprint-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Demo
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            val previewPane = PreviewPaneCoordinator()
            val projectId = project.locationHash

            processManager.start(projectId = projectId, workingDir = projectRoot.toString())
            previewPane.open(projectId, "http://127.0.0.1:8000/")

            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
            )

            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val result = service.startPreview()

            assertTrue(result.success)
            assertEquals("Preview already running.", result.message)
            assertEquals(1, launcher.launchCount)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `startPreview reloads mkdocs config changes by restarting runtime`() {
        val projectRoot = createTempDirectory(prefix = "runtime-config-reload-")
        try {
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Demo
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            val previewPane = PreviewPaneCoordinator()

            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
            )

            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val first = service.startPreview()
            assertTrue(first.success)
            assertEquals(1, launcher.launchCount)

            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Demo
                    docs_dir: docs
                    theme:
                      name: material
                    nav: []
                """.trimIndent() + "\n",
            )

            val second = service.startPreview()
            assertTrue(second.success)
            assertEquals(2, launcher.launchCount)
            assertTrue(second.message != "Preview already running.")
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `restartPreview restarts runtime process and re-runs activation flow`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val launcher = CountingProcessLauncher()
        val processManager = MkdocsProcessManager(launcher)
        val previewPane = PreviewPaneCoordinator()

        val dependencies = RuntimeIntegrationDependencies(
            activationService = PluginActivationService(
                bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                processManager = processManager,
                baseUrlDetector = BaseUrlDetector(),
                previewPaneCoordinator = previewPane,
                errorPresenter = ActivationErrorPresenter(),
                readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
            ),
            processManager = processManager,
            previewPaneCoordinator = previewPane,
            navigationCoordinator = NavigationCoordinator(
                routeMappingService = RouteMappingService(),
                previewPaneCoordinator = previewPane,
                failureHandler = PreviewNavigationFailureHandler(),
            ),
            featureFlagPolicyService = FeatureFlagPolicyService(),
            startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
        )

        val service = PluginRuntimeIntegrationService(project)
        service.overrideDependenciesForTesting(dependencies)

        val initial = service.startPreview()
        val restarted = service.restartPreview(PreviewStartTrigger.TOOL_WINDOW)

        assertTrue(initial.success)
        assertTrue(restarted.success)
        assertEquals(2, launcher.launchCount)
        assertTrue(service.isRuntimeRunning())
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun `restartPreview preserves current route after runtime restart`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val launcher = CountingProcessLauncher()
        val processManager = MkdocsProcessManager(launcher)
        val previewPane = PreviewPaneCoordinator()

        val dependencies = RuntimeIntegrationDependencies(
            activationService = PluginActivationService(
                bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                processManager = processManager,
                baseUrlDetector = BaseUrlDetector(),
                previewPaneCoordinator = previewPane,
                errorPresenter = ActivationErrorPresenter(),
                readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
            ),
            processManager = processManager,
            previewPaneCoordinator = previewPane,
            navigationCoordinator = NavigationCoordinator(
                routeMappingService = RouteMappingService(),
                previewPaneCoordinator = previewPane,
                failureHandler = PreviewNavigationFailureHandler(),
            ),
            featureFlagPolicyService = FeatureFlagPolicyService(),
            startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
        )

        val service = PluginRuntimeIntegrationService(project)
        service.overrideDependenciesForTesting(dependencies)

        assertTrue(service.startPreview().success)
        val navigated = service.navigateToSelectedFile("/tmp/project/docs/guides/index.md")
        assertTrue(navigated != null && navigated.endsWith("/guides/"))

        val restarted = service.restartPreview(PreviewStartTrigger.TOOL_WINDOW)

        assertTrue(restarted.success)
        assertEquals(2, launcher.launchCount)
        assertTrue(restarted.previewUrl.endsWith("/guides/"))
        val restartedCurrent = service.currentPreviewUrl()
        assertTrue(restartedCurrent != null && restartedCurrent.endsWith("/guides/"))
    }

    @Test
    fun `dispose stops running runtime process for project`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")

        service.startPreview()
        assertTrue(service.isRuntimeRunning())

        service.dispose()

        assertFalse(service.isRuntimeRunning())
    }

    @Test
    fun `project startup output provider reads value from project user data`() {
        val project = IntellijTestFixtures.project()
        val provider = ProjectUserDataStartupOutputProvider()
        val expected = " log includes https://preview.example/"
        project.putUserData(ProjectUserDataStartupOutputProvider.KEY, expected)

        val resolved = provider.startupOutput(project, PreviewStartTrigger.ACTION)

        assertEquals(expected, resolved)
    }

    @Test
    fun `failed start keeps start action re-invokable`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val launcher = CountingProcessLauncher()
        val processManager = MkdocsProcessManager(launcher)
        val previewPane = PreviewPaneCoordinator()
        var now = 0L
        val dependencies = RuntimeIntegrationDependencies(
            activationService = PluginActivationService(
                bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                processManager = processManager,
                baseUrlDetector = BaseUrlDetector(),
                previewPaneCoordinator = previewPane,
                errorPresenter = ActivationErrorPresenter(),
                readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { false },
                maxStartupAttempts = 1,
                startupProbeTimeoutMillis = 1L,
                startupPollIntervalMillis = 1L,
                nowMillisProvider = { now++ },
            ),
            processManager = processManager,
            previewPaneCoordinator = previewPane,
            navigationCoordinator = NavigationCoordinator(
                routeMappingService = RouteMappingService(),
                previewPaneCoordinator = previewPane,
                failureHandler = PreviewNavigationFailureHandler(),
            ),
            featureFlagPolicyService = FeatureFlagPolicyService(),
            startupOutputProvider = StartupOutputProvider { _, _ -> "" },
        )
        val service = PluginRuntimeIntegrationService(project)
        service.overrideDependenciesForTesting(dependencies)

        val result = service.startPreview()

        assertFalse(result.success)
        assertFalse(service.isRuntimeRunning())
        assertTrue(service.canStartPreview())
    }

    @Test
    fun `navigateToSelectedFile maps docs markdown to preview route`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val updatedUrl = service.navigateToSelectedFile("/tmp/project/docs/guides/index.md")

        assertTrue(updatedUrl != null && updatedUrl.endsWith("/guides/"))
    }

    @Test
    fun `navigateToSelectedFile ignores non docs files`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val updatedUrl = service.navigateToSelectedFile("/tmp/project/README.md")

        assertNull(updatedUrl)
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun `usesDirtyLivereloadServeMode reflects running command flags`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val launcher = CountingProcessLauncher()
        val processManager = MkdocsProcessManager(launcher)
        val previewPane = PreviewPaneCoordinator()

        val dependencies = RuntimeIntegrationDependencies(
            activationService = PluginActivationService(
                bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                processManager = processManager,
                baseUrlDetector = BaseUrlDetector(),
                previewPaneCoordinator = previewPane,
                errorPresenter = ActivationErrorPresenter(),
                readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
            ),
            processManager = processManager,
            previewPaneCoordinator = previewPane,
            navigationCoordinator = NavigationCoordinator(
                routeMappingService = RouteMappingService(),
                previewPaneCoordinator = previewPane,
                failureHandler = PreviewNavigationFailureHandler(),
            ),
            featureFlagPolicyService = FeatureFlagPolicyService(),
            startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
        )

        val service = PluginRuntimeIntegrationService(project)
        service.overrideDependenciesForTesting(dependencies)

        assertFalse(service.usesDirtyLivereloadServeMode())
        assertTrue(service.startPreview().success)
        assertTrue(launcher.lastCommand.contains("--livereload"))
        assertTrue(launcher.lastCommand.contains("--dirty"))
        assertTrue(service.usesDirtyLivereloadServeMode())
    }

    @Test
    fun `buildPreviewRouteIntent resolves route using served docs_dir scope`() {
        val projectRoot = createTempDirectory(prefix = "runtime-preview-intent-scope-")
        try {
            val docsAltDir = Files.createDirectories(projectRoot.resolve("docs-alt"))
            val guidePath = docsAltDir.resolve("guide").also(Files::createDirectories).resolve("index.md")
            Files.writeString(guidePath, "# Guide\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs-alt
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-intent-scope")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "http://127.0.0.1:8000/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val intent = service.buildPreviewRouteIntent(
                selectedPath = guidePath.toString(),
                source = PreviewRouteIntentSource.DIRECT_NAVIGATION,
            )

            assertTrue(intent != null)
            assertEquals("/guide/", intent.route)
            assertEquals(docsAltDir.toAbsolutePath().normalize().toString().replace('\\', '/'), intent.docsDirPath)
            assertTrue(service.currentPreviewUrl()?.endsWith("/guide/") == true)
            assertTrue(service.isPreviewEligibleMarkdownPath(guidePath.toString()))
            assertFalse(service.isPreviewEligibleMarkdownPath(projectRoot.resolve("README.md").toString()))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `buildPreviewRouteIntent treats docs-local mkdocs config without docs_dir as docs scoped`() {
        val projectRoot = createTempDirectory(prefix = "runtime-preview-intent-docs-local-config-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
            val pagePath = docsDir.resolve("quick-start.md")
            Files.writeString(pagePath, "# Quick Start\n")
            val configPath = docsDir.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    nav:
                      - quick-start.md
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-intent-docs-local-config")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val intent = service.buildPreviewRouteIntent(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.DIRECT_NAVIGATION,
            )

            assertTrue(intent != null)
            assertEquals("/quick-start/", intent.route)
            assertEquals(docsDir.toAbsolutePath().normalize().toString().replace('\\', '/'), intent.docsDirPath)
            assertTrue(service.isPreviewEligibleMarkdownPath(pagePath.toString()))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `dispatchPreviewForSelectedFile forwards forceReload flag to dispatched load`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-force-reload-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs").resolve("guides"))
            val guidePath = docsDir.resolve("index.md")
            Files.writeString(guidePath, "# Guide\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "dispatch-force-reload")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            val loaded = mutableListOf<Pair<String, Boolean>>()

            val dispatched = service.dispatchPreviewForSelectedFile(
                selectedPath = guidePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
            )

            assertTrue(dispatched)
            assertEquals(listOf("https://preview.example/guides/" to true), loaded)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `buildPreviewRouteIntent honors use_directory_urls false and uppercase markdown extension`() {
        val projectRoot = createTempDirectory(prefix = "runtime-preview-intent-html-route-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
            val pagePath = docsDir.resolve("Guide.MD")
            Files.writeString(pagePath, "# Guide\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                    use_directory_urls: false
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-intent-html-route")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val intent = service.buildPreviewRouteIntent(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.DIRECT_NAVIGATION,
            )

            assertTrue(intent != null)
            assertEquals("/Guide.html", intent.route)
            assertEquals("https://preview.example/Guide.html", intent.targetUrl)
            assertTrue(service.isPreviewEligibleMarkdownPath(pagePath.toString()))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `buildPreviewRouteIntent reads inherited use_directory_urls from fallback config`() {
        val projectRoot = createTempDirectory(prefix = "runtime-preview-intent-inherited-html-route-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
            val pagePath = docsDir.resolve("guide.md")
            Files.writeString(pagePath, "# Guide\n")

            val baseConfig = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                baseConfig,
                """
                    site_name: Demo
                    docs_dir: docs
                    use_directory_urls: false
                """.trimIndent() + "\n",
            )

            val fallbackConfig = projectRoot.resolve(".authord.theme.yml")
            Files.writeString(
                fallbackConfig,
                """
                    INHERIT: '${baseConfig.toAbsolutePath().normalize()}'
                    docs_dir: '${docsDir.toAbsolutePath().normalize()}'
                    theme:
                      name: mkdocs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-intent-inherited-html-route")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        fallbackConfig.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val intent = service.buildPreviewRouteIntent(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.DIRECT_NAVIGATION,
            )

            assertTrue(intent != null)
            assertEquals("/guide.html", intent.route)
            assertEquals("https://preview.example/guide.html", intent.targetUrl)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `topic mutation preview dispatch waits for source file to exist before loading browser`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-file-ready-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs").resolve("guides"))
            val missingPath = docsDir.resolve("new-page.md")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "dispatch-file-ready")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            val loaded = mutableListOf<Pair<String, Boolean>>()
            var unavailableMessage: String? = null

            val beforeCreate = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = missingPath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
                onRouteUnavailable = { message -> unavailableMessage = message },
            )

            assertFalse(beforeCreate)
            assertTrue(loaded.isEmpty())
            assertNull(unavailableMessage)

            Files.writeString(missingPath, "# New Page\n")
            unavailableMessage = null
            val afterCreate = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = missingPath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
                onRouteUnavailable = { message -> unavailableMessage = message },
            )

            assertTrue(afterCreate)
            assertEquals(listOf("https://preview.example/guides/new-page/" to true), loaded)
            assertNull(unavailableMessage)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `on topic mutation commit updates verified preview url cache from mutation file operations`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-cache-rebuild-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(docsDir.resolve("guide").also(Files::createDirectories).resolve("index.md"), "# Guide\n")
            Files.writeString(docsDir.resolve("tutorials").also(Files::createDirectories).resolve("setup.md"), "# Setup\n")
            Files.writeString(
                docsDir
                    .resolve("nested")
                    .resolve("deep")
                    .also(Files::createDirectories)
                    .resolve("page.md"),
                "# Deep Page\n",
            )
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "dispatch-cache-rebuild")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            assertTrue(cachedPreviewTargetUrls(service).isEmpty())
            service.onTopicMutationCommitted(
                fileOperations = listOf(
                    TopicFileOperation(TopicFileOperationKind.CREATE, "guide/index.md"),
                    TopicFileOperation(TopicFileOperationKind.CREATE, "tutorials/setup.md"),
                    TopicFileOperation(TopicFileOperationKind.CREATE, "nested/deep/page.md"),
                ),
            )

            val cachedUrls = cachedPreviewTargetUrls(service)
            assertEquals(
                setOf(
                    "https://preview.example/guide/",
                    "https://preview.example/tutorials/setup/",
                    "https://preview.example/nested/deep/page/",
                ),
                cachedUrls,
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `onTopicMutationCommitted nudges dirty livereload for nav-backed toc mutations without restart`() {
        val projectRoot = createTempDirectory(prefix = "runtime-topic-mutation-nav-livereload-")
        try {
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                    nav: []
                """.trimIndent() + "\n",
            )
            Files.setLastModifiedTime(configPath, FileTime.fromMillis(1_000L))

            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "mutation-nav-livereload",
            )
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            assertEquals(1, launcher.launchCount)

            val firstMutationStartedAt = System.nanoTime()
            val firstMutation = service.onTopicMutationCommitted(navPresent = true)
            val firstMutationElapsedMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - firstMutationStartedAt,
            )
            val secondMutation = service.onTopicMutationCommitted(navPresent = true)

            assertTrue(firstMutation)
            assertTrue(secondMutation)
            assertTrue(firstMutationElapsedMs >= 150L)
            assertTrue(Files.getLastModifiedTime(configPath).toMillis() > 1_000L)
            assertEquals(1, launcher.launchCount)
            assertTrue(service.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `onTopicMutationCommitted does not nudge dirty livereload when nav is absent`() {
        val projectRoot = createTempDirectory(prefix = "runtime-topic-mutation-no-nav-livereload-")
        try {
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )
            Files.setLastModifiedTime(configPath, FileTime.fromMillis(1_000L))

            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "mutation-no-nav-livereload",
            )
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            assertEquals(1, launcher.launchCount)

            val nudged = service.onTopicMutationCommitted(navPresent = false)

            assertFalse(nudged)
            assertEquals(1_000L, Files.getLastModifiedTime(configPath).toMillis())
            assertEquals(1, launcher.launchCount)
            assertTrue(service.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `topic mutation retry refresh does not nudge dirty livereload when nav is absent`() {
        val projectRoot = createTempDirectory(prefix = "runtime-topic-retry-refresh-no-nav-")
        try {
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )
            Files.setLastModifiedTime(configPath, FileTime.fromMillis(1_000L))

            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "retry-refresh-no-nav",
            )
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "ready at https://preview.example/" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            service.onTopicMutationCommitted(navPresent = false)

            val refreshMethod = PluginRuntimeIntegrationService::class.java.getDeclaredMethod(
                "refreshPreviewStateForRetry",
                PreviewRouteIntentSource::class.java,
            )
            refreshMethod.isAccessible = true
            refreshMethod.invoke(service, PreviewRouteIntentSource.TOPIC_MUTATION)

            assertEquals(1_000L, Files.getLastModifiedTime(configPath).toMillis())
            assertEquals(1, launcher.launchCount)
            assertTrue(service.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `topic mutation preview dispatch keeps cached local routes behind readiness guard`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-local-immediate-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs").resolve("guides"))
            val pagePath = docsDir.resolve("new-page.md")
            Files.writeString(pagePath, "# New Page\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "dispatch-local-immediate")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "http://127.0.0.1:65530/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            val loaded = mutableListOf<Pair<String, Boolean>>()
            val expectedUrl = "http://127.0.0.1:65530/guides/new-page/"

            service.onTopicMutationCommitted(
                fileOperations = listOf(
                    TopicFileOperation(TopicFileOperationKind.CREATE, "guides/new-page.md"),
                ),
            )
            assertTrue(cachedPreviewTargetUrls(service).contains(expectedUrl))

            val dispatched = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
            )

            assertTrue(dispatched)
            assertTrue(loaded.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `topic mutation preview dispatch does not require config fingerprint changes when nav is absent`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-no-nav-fingerprint-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs").resolve("guides"))
            val pagePath = docsDir.resolve("new-page.md")
            Files.writeString(pagePath, "# New Page\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )

            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "dispatch-no-nav-fingerprint",
            )
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            val loaded = mutableListOf<Pair<String, Boolean>>()
            val expectedLoad = "https://preview.example/guides/new-page/" to true

            val firstMutation = service.onTopicMutationCommitted(navPresent = false)
            val firstDispatch = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
            )
            val secondMutation = service.onTopicMutationCommitted(navPresent = false)
            val secondDispatch = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
            )

            assertFalse(firstMutation)
            assertTrue(firstDispatch)
            assertFalse(secondMutation)
            assertTrue(secondDispatch)
            assertEquals(listOf(expectedLoad, expectedLoad), loaded)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `topic mutation preview dispatch waits for mkdocs config to exist before loading browser`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dispatch-config-ready-")
        try {
            val docsDir = Files.createDirectories(projectRoot.resolve("docs").resolve("guides"))
            val pagePath = docsDir.resolve("new-page.md")
            Files.writeString(pagePath, "# New Page\n")
            val configPath = projectRoot.resolve("mkdocs.yml")
            val configContent = """
                site_name: Demo
                docs_dir: docs
            """.trimIndent() + "\n"
            Files.writeString(configPath, configContent)

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "dispatch-config-ready")
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(project.locationHash, "https://preview.example/")
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)
            val loaded = mutableListOf<Pair<String, Boolean>>()
            var unavailableMessage: String? = null

            service.onTopicMutationCommitted()
            Files.delete(configPath)

            val beforeRestore = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
                onRouteUnavailable = { message -> unavailableMessage = message },
            )

            assertFalse(beforeRestore)
            assertTrue(loaded.isEmpty())
            assertNull(unavailableMessage)

            Files.writeString(configPath, configContent)
            unavailableMessage = null
            val afterRestore = service.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = pagePath.toString(),
                source = PreviewRouteIntentSource.TOPIC_MUTATION,
                forceReload = true,
                retryAttempts = 0,
                loadUrl = { url, forceReload -> loaded += url to forceReload },
                onRouteUnavailable = { message -> unavailableMessage = message },
            )

            assertTrue(afterRestore)
            assertEquals(listOf("https://preview.example/guides/new-page/" to true), loaded)
            assertNull(unavailableMessage)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `onTopicMutationCommitted clears browser last loaded url cache when browser service exists`() {
        val browserHostProject = IntellijTestFixtures.project(locationHash = "mutation-browser-host")
        val browserService = MkDocsPreviewBrowserService(browserHostProject)
        browserService.loadUrl("http://127.0.0.1:8000/guide/")
        assertEquals("http://127.0.0.1:8000/guide/", sharedLastLoadedUrl(browserService))

        val project = IntellijTestFixtures.project(
            locationHash = "mutation-browser-reset",
            services = mapOf(MkDocsPreviewBrowserService::class.java to browserService),
        )
        val service = PluginRuntimeIntegrationService(project)

        val nudged = service.onTopicMutationCommitted()

        assertFalse(nudged)
        assertNull(sharedLastLoadedUrl(browserService))
    }

    @Test
    fun `onTopicMutationCommittedAsync falls back to synchronous execution when application is unavailable`() {
        val projectRoot = createTempDirectory(prefix = "runtime-topic-mutation-async-fallback-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "mutation-async-fallback")
            val service = PluginRuntimeIntegrationService(project)
            var callbackResult: Boolean? = null

            service.onTopicMutationCommittedAsync(navPresent = false) { result ->
                callbackResult = result
            }

            assertEquals(false, callbackResult)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `onTopicMutationCommitted does not force browser navigation when runtime is live`() {
        val projectRoot = createTempDirectory(prefix = "runtime-topic-mutation-force-reload-")
        try {
            val configPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )
            val projectId = "mutation-force-reload"
            val browserHostProject = IntellijTestFixtures.project(locationHash = "mutation-force-reload-browser-host")
            val browserService = MkDocsPreviewBrowserService(browserHostProject)
            val recordingPreview = MutationReloadRecordingPreviewContent()
            injectPreviewContent(browserService, recordingPreview)
            val currentUrl = "http://127.0.0.1:8000/guides/"
            browserService.loadUrl(currentUrl)
            assertEquals(listOf(currentUrl), recordingPreview.loadedUrls)

            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = projectId,
                services = mapOf(MkDocsPreviewBrowserService::class.java to browserService),
            )
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = projectId,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "-f",
                        configPath.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            previewPane.open(projectId, currentUrl)
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val nudged = service.onTopicMutationCommitted()

            assertFalse(nudged)
            assertEquals(listOf(currentUrl), recordingPreview.loadedUrls)
            assertNull(sharedLastLoadedUrl(browserService))
            assertEquals(1, launcher.launchCount)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `nudgeDirtyLivereloadReload touches watched fallback config without restart`() {
        val projectRoot = createTempDirectory(prefix = "runtime-dirty-livereload-nudge-")
        try {
            val fallbackConfig = projectRoot.resolve(".authord.theme.yml")
            Files.writeString(fallbackConfig, "INHERIT: 'mkdocs.yml'\n")
            Files.setLastModifiedTime(fallbackConfig, FileTime.fromMillis(1_000L))

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val launcher = CountingProcessLauncher()
            val processManager = MkdocsProcessManager(launcher)
            processManager.start(
                projectId = project.locationHash,
                workingDir = projectRoot.toString(),
                config = com.authord.mkdocs.runtime.RuntimeServerConfig(
                    command = listOf(
                        "mkdocs",
                        "serve",
                        "--livereload",
                        "--dirty",
                        "-f",
                        fallbackConfig.toString(),
                    ),
                ),
            )
            val previewPane = PreviewPaneCoordinator()
            val dependencies = RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(SuccessCommandRunner()),
                    processManager = processManager,
                    baseUrlDetector = BaseUrlDetector(),
                    previewPaneCoordinator = previewPane,
                    errorPresenter = ActivationErrorPresenter(),
                    readinessProbe = com.authord.mkdocs.ui.HttpReadinessProbe { true },
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPane,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPane,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = StartupOutputProvider { _, _ -> "" },
            )
            val service = PluginRuntimeIntegrationService(project)
            service.overrideDependenciesForTesting(dependencies)

            val nudged = service.nudgeDirtyLivereloadReload()
            val nudgedOnMutation = service.onTopicMutationCommitted(navPresent = true)

            assertTrue(nudged)
            assertTrue(nudgedOnMutation)
            val modifiedTime = Files.getLastModifiedTime(fallbackConfig).toMillis()
            assertTrue(modifiedTime > 1_000L)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    private fun sharedLastLoadedUrl(browserService: MkDocsPreviewBrowserService): String? {
        val field = MkDocsPreviewBrowserService::class.java.getDeclaredField("sharedLastLoadedUrl")
        field.isAccessible = true
        return field.get(browserService) as? String
    }

    private fun injectPreviewContent(service: MkDocsPreviewBrowserService, preview: PreviewContent) {
        val field = MkDocsPreviewBrowserService::class.java.getDeclaredField("previewContent")
        field.isAccessible = true
        field.set(service, preview)
    }

    private fun cachedPreviewTargetUrls(service: PluginRuntimeIntegrationService): Set<String> {
        val cacheField = PluginRuntimeIntegrationService::class.java.getDeclaredField("verifiedPreviewRouteCacheState")
        cacheField.isAccessible = true
        val state = cacheField.get(service) ?: return emptySet()
        val entriesField = state.javaClass.getDeclaredField("entries")
        entriesField.isAccessible = true
        val raw = entriesField.get(state) as? Map<*, *> ?: return emptySet()
        return raw.values.mapNotNull { entry ->
            val targetUrlField = runCatching { entry?.javaClass?.getDeclaredField("targetUrl") }.getOrNull() ?: return@mapNotNull null
            targetUrlField.isAccessible = true
            targetUrlField.get(entry) as? String
        }.toSet()
    }
}
