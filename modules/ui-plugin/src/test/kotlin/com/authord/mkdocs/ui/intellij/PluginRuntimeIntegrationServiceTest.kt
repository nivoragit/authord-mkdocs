package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ui.ActivationErrorPresenter
import com.authord.mkdocs.ui.FeatureFlagPolicyService
import com.authord.mkdocs.ui.NavigationCoordinator
import com.authord.mkdocs.ui.PluginActivationService
import com.authord.mkdocs.ui.PreviewNavigationFailureHandler
import com.authord.mkdocs.ui.PreviewPaneCoordinator
import com.authord.mkdocs.core.navigation.RouteMappingService
import java.nio.file.Files
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

    override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
        launchCount += 1
        return LifecycleHandle("process-${counter.incrementAndGet()}")
    }
}

private class SuccessCommandRunner : CommandRunner {
    override fun run(command: List<String>, workingDir: String): CommandResult = CommandResult(exitCode = 0)
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
}
