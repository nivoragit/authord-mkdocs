package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ui.ActivationErrorPresenter
import com.authord.mkdocs.ui.ActivationFailureReason
import com.authord.mkdocs.ui.ActivationResult
import com.authord.mkdocs.ui.FeatureFlagPolicyService
import com.authord.mkdocs.ui.NavigationCoordinator
import com.authord.mkdocs.ui.PluginActivationService
import com.authord.mkdocs.ui.PreviewNavigationFailureHandler
import com.authord.mkdocs.ui.PreviewPaneCoordinator
import com.authord.mkdocs.core.navigation.RouteMappingService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/** API version for plugin runtime integration service. */
const val PLUGIN_RUNTIME_INTEGRATION_API_VERSION: String = "1.0.0"

/**
 * Trigger source used to start plugin preview runtime flow.
 */
enum class PreviewStartTrigger {
    ACTION,
    TOOL_WINDOW,
}

/**
 * Strategy for supplying startup output text consumed by base-URL detection.
 */
fun interface StartupOutputProvider {
    /**
     * Returns runtime startup output text for the provided project and trigger.
     */
    fun startupOutput(project: Project, trigger: PreviewStartTrigger): String
}

/**
 * Shared runtime integration dependencies used by IntelliJ shell entry points.
 */
data class RuntimeIntegrationDependencies(
    val activationService: PluginActivationService,
    val processManager: MkdocsProcessManager,
    val previewPaneCoordinator: PreviewPaneCoordinator,
    val navigationCoordinator: NavigationCoordinator,
    val featureFlagPolicyService: FeatureFlagPolicyService,
    val startupOutputProvider: StartupOutputProvider,
) {
    companion object {
        /**
         * Creates MVP-safe default wiring that reuses existing runtime/domain services.
         */
        fun createDefault(): RuntimeIntegrationDependencies {
            val useInMemoryAdapters = System.getProperty("org.gradle.test.worker") != null
            val commandRunner: CommandRunner = if (useInMemoryAdapters) {
                InMemoryCommandRunner()
            } else {
                ProcessBuilderCommandRunner()
            }
            val processLauncher: ProcessLauncher = if (useInMemoryAdapters) {
                InMemoryProcessLauncher()
            } else {
                ProcessBuilderProcessLauncher()
            }
            val processManager = MkdocsProcessManager(processLauncher)
            val previewPaneCoordinator = PreviewPaneCoordinator()
            return RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(commandRunner),
                    processManager = processManager,
                    baseUrlDetector = com.authord.mkdocs.runtime.BaseUrlDetector(),
                    previewPaneCoordinator = previewPaneCoordinator,
                    errorPresenter = ActivationErrorPresenter(),
                ),
                processManager = processManager,
                previewPaneCoordinator = previewPaneCoordinator,
                navigationCoordinator = NavigationCoordinator(
                    routeMappingService = RouteMappingService(),
                    previewPaneCoordinator = previewPaneCoordinator,
                    failureHandler = PreviewNavigationFailureHandler(),
                ),
                featureFlagPolicyService = FeatureFlagPolicyService(),
                startupOutputProvider = ProjectUserDataStartupOutputProvider(),
            )
        }
    }
}

/**
 * Project-scoped facade used by IntelliJ action/tool-window entry points.
 *
 * API Version: [PLUGIN_RUNTIME_INTEGRATION_API_VERSION]
 */
@Service(Service.Level.PROJECT)
class PluginRuntimeIntegrationService(
    private val project: Project,
) : Disposable {
    private var dependencies: RuntimeIntegrationDependencies = RuntimeIntegrationDependencies.createDefault()

    /**
     * Overrides runtime integration dependencies for unit tests.
     *
     * This method is intentionally internal to prevent production callers from
     * mutating service wiring at runtime.
     */
    internal fun overrideDependenciesForTesting(
        dependencies: RuntimeIntegrationDependencies,
    ) {
        this.dependencies = dependencies
    }

    /**
     * Returns `true` when preview start action may run for this project.
     */
    fun canStartPreview(): Boolean {
        val projectPath = project.basePath ?: return false
        val policy = dependencies.featureFlagPolicyService.current()
        return projectPath.isNotBlank() &&
            policy.allowsMvpFlow() &&
            policy.disallowsFutureCycleFeatures()
    }

    /**
     * Starts preview orchestration through existing activation/runtime services.
     *
     * @param trigger source entry point for diagnostics and traceability.
     * @return activation result with success, failure reason, and optional preview URL.
     */
    fun startPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        val projectPath = project.basePath
            ?: return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Project base path is not available for runtime start.",
            )

        val projectId = project.locationHash
        val existingPreviewUrl = dependencies.previewPaneCoordinator.currentUrl(projectId)
        if (dependencies.processManager.isRunning(projectId) && existingPreviewUrl != null) {
            return ActivationResult(
                success = true,
                previewUrl = existingPreviewUrl,
                message = "Preview already running.",
            )
        }

        return dependencies.activationService.activate(
            projectId = projectId,
            projectPath = projectPath,
            startupOutput = dependencies.startupOutputProvider.startupOutput(project, trigger),
            featureFlags = dependencies.featureFlagPolicyService.current(),
        )
    }

    /**
     * Stops active runtime instance for this project.
     */
    fun stopPreview(): Boolean = dependencies.processManager.stop(project.locationHash)

    /**
     * Returns `true` when runtime is currently active for this project.
     */
    fun isRuntimeRunning(): Boolean = dependencies.processManager.isRunning(project.locationHash)

    /**
     * Restarts active runtime flow for this project.
     *
     * If runtime is not currently running, this method starts preview instead.
     */
    fun restartPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        val projectId = project.locationHash
        val previousRoute = dependencies.previewPaneCoordinator.currentState(projectId)?.currentRoute
        if (dependencies.processManager.isRunning(projectId)) {
            dependencies.processManager.stop(projectId)
        }
        val restarted = startPreview(trigger)
        if (!restarted.success) {
            return restarted
        }

        if (previousRoute != null && previousRoute != "/") {
            dependencies.previewPaneCoordinator.navigate(projectId, previousRoute)
        }

        return restarted.copy(previewUrl = currentPreviewUrl().orEmpty())
    }

    /**
     * Returns the latest preview URL tracked for this project.
     */
    fun currentPreviewUrl(): String? = dependencies.previewPaneCoordinator.currentUrl(project.locationHash)

    /**
     * Applies preview route navigation for the selected editor file path.
     *
     * @param selectedPath absolute or project-relative editor file path.
     * @return updated preview URL when navigation is applied, otherwise `null`.
     */
    fun navigateToSelectedFile(selectedPath: String): String? {
        if (!isRuntimeRunning()) {
            return null
        }

        val relativePath = projectRelativePath(selectedPath) ?: return null
        val navigationResult = dependencies.navigationCoordinator.onFileSelected(project.locationHash, relativePath)
        if (!navigationResult.applied) {
            return null
        }

        return currentPreviewUrl()
    }

    /**
     * Replaces active feature-flag policy for this project integration service.
     */
    fun updateFeatureFlags(policy: FeatureFlagPolicy) {
        dependencies.featureFlagPolicyService.update(policy)
    }

    /**
     * Stores startup output used on the next activation attempt.
     */
    fun setStartupOutputForNextRun(startupOutput: String) {
        project.putUserData(ProjectUserDataStartupOutputProvider.KEY, startupOutput)
    }

    /**
     * Disposes project runtime resources safely.
     */
    override fun dispose() {
        dependencies.processManager.dispose(project.locationHash)
    }

    private fun projectRelativePath(selectedPath: String): String? {
        val normalized = selectedPath.replace('\\', '/')
        val projectPath = project.basePath ?: return null
        if (!Path.of(selectedPath).isAbsolute) {
            return normalized.trimStart('/')
        }

        return try {
            Path.of(projectPath).relativize(Path.of(selectedPath)).toString().replace('\\', '/')
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Reads startup output from project user data to avoid hardcoded endpoint assumptions.
 */
class ProjectUserDataStartupOutputProvider : StartupOutputProvider {
    /**
     * Resolves startup output text from project user data.
     */
    override fun startupOutput(project: Project, trigger: PreviewStartTrigger): String {
        return project.getUserData(KEY).orEmpty()
    }

    companion object {
        /** Project key storing startup output consumed by base-URL detection. */
        val KEY: Key<String> = Key.create("authord.mkdocs.startupOutput")
    }
}

/**
 * In-memory command runner used by plugin shell defaults.
 */
class InMemoryCommandRunner : CommandRunner {
    /**
     * Returns a successful in-memory command execution result for shell defaults.
     */
    override fun run(command: List<String>, workingDir: String): CommandResult {
        return CommandResult(exitCode = 0, stdout = command.joinToString(" "))
    }
}

private class InMemoryManagedProcessHandle(
    override val id: String,
) : ManagedProcessHandle {
    private var alive: Boolean = true

    /**
     * Marks this in-memory handle as stopped.
     */
    override fun stop() {
        alive = false
    }

    /**
     * Returns whether this in-memory process handle is still active.
     */
    override fun isAlive(): Boolean = alive
}

/**
 * In-memory process launcher used to preserve lifecycle/single-instance semantics in shell mode.
 */
class InMemoryProcessLauncher : ProcessLauncher {
    private val counter = AtomicInteger(0)

    /**
     * Launches an in-memory process handle for lifecycle/single-instance testing and shell defaults.
     */
    override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
        val processId = "process-${counter.incrementAndGet()}"
        return InMemoryManagedProcessHandle(id = processId)
    }
}
