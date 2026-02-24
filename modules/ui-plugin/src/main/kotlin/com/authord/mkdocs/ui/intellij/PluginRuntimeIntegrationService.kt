package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.ProjectManagedUvExecutableProvider
import com.authord.mkdocs.runtime.StaticUvExecutableProvider
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
import com.intellij.ui.JBColor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** API version for plugin runtime integration service. */
const val PLUGIN_RUNTIME_INTEGRATION_API_VERSION: String = "1.0.0"
private const val DEFAULT_PREVIEW_DISPATCH_RETRY_ATTEMPTS: Int = 2
private const val DEFAULT_PREVIEW_DISPATCH_RETRY_DELAY_MS: Long = 1_200L
private const val TOPIC_MUTATION_VERIFICATION_HOLD_DELAY_MS: Long = 150L

/**
 * Trigger source used to start plugin preview runtime flow.
 */
enum class PreviewStartTrigger {
    ACTION,
    TOOL_WINDOW,
}

enum class PreviewRouteIntentSource {
    MARKDOWN_OPEN,
    SPLIT_EDITOR,
    TOOL_WINDOW_SELECTION,
    TOPIC_MUTATION,
    DIRECT_NAVIGATION,
}

data class PreviewRouteIntent(
    val selectedPath: String,
    val route: String,
    val targetUrl: String,
    val docsDirPath: String?,
    val configPath: String?,
    val dirtyLivereloadMode: Boolean,
    val source: PreviewRouteIntentSource,
)

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
            val uvExecutableProvider = if (useInMemoryAdapters) {
                StaticUvExecutableProvider()
            } else {
                ProjectManagedUvExecutableProvider()
            }
            val processManager = MkdocsProcessManager(processLauncher)
            val previewPaneCoordinator = PreviewPaneCoordinator()
            return RuntimeIntegrationDependencies(
                activationService = PluginActivationService(
                    bootstrapService = UvBootstrapService(commandRunner, uvExecutableProvider),
                    processManager = processManager,
                    baseUrlDetector = com.authord.mkdocs.runtime.BaseUrlDetector(),
                    previewPaneCoordinator = previewPaneCoordinator,
                    errorPresenter = ActivationErrorPresenter(),
                    isDarkIdeTheme = { !JBColor.isBright() },
                    readinessProbe = if (useInMemoryAdapters) {
                        com.authord.mkdocs.ui.HttpReadinessProbe { true }
                    } else {
                        com.authord.mkdocs.ui.HttpURLConnectionReadinessProbe()
                    },
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
    private data class VerifiedPreviewRouteEntry(
        val route: String,
        val targetUrl: String,
    )

    private var dependencies: RuntimeIntegrationDependencies = RuntimeIntegrationDependencies.createDefault()
    private val routeMappingService = RouteMappingService()
    private val previewRuntimeService = MkDocsPreviewService(
        processManagerProvider = { dependencies.processManager },
        projectIdProvider = { project.locationHash },
    )
    private var lastConfigFingerprint: String? = null
    private val previewRouteIntentGeneration = AtomicInteger(0)
    @Volatile
    private var verifiedPreviewRouteCache: Map<String, VerifiedPreviewRouteEntry> = emptyMap()
    @Volatile
    private var verifiedPreviewRouteCacheBaseUrl: String? = null
    @Volatile
    private var topicMutationConfigVerificationPending: Boolean = false
    @Volatile
    private var lastVerifiedTopicMutationConfigFingerprint: String? = null
    @Volatile
    private var navPresentFromParsedConfigState: Boolean = false

    init {
        Disposer.register(this, previewRuntimeService)
    }

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
    @Synchronized
    fun startPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        return startPreviewInternal(
            trigger = trigger,
            allowDependencyBypass = false,
            retryDependencySetup = false,
        )
    }

    /**
     * Forces dependency setup retry (re-bootstrap + strict verification) before startup.
     */
    @Synchronized
    fun retryDependencySetup(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        return startPreviewInternal(
            trigger = trigger,
            allowDependencyBypass = false,
            retryDependencySetup = true,
        )
    }

    /**
     * Starts preview while bypassing a blocking dependency-setup failure once.
     */
    @Synchronized
    fun startPreviewAnyway(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        return startPreviewInternal(
            trigger = trigger,
            allowDependencyBypass = true,
            retryDependencySetup = false,
        )
    }

    private fun startPreviewInternal(
        trigger: PreviewStartTrigger,
        allowDependencyBypass: Boolean,
        retryDependencySetup: Boolean,
    ): ActivationResult {
        val projectPath = project.basePath
            ?: return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Project base path is not available for runtime start.",
            )

        val projectId = project.locationHash
        val hasKnownConfigFingerprint = lastConfigFingerprint != null
        val configChanged = hasConfigChanged(projectPath)
        if (previewRuntimeService.isServerRunning() && hasKnownConfigFingerprint && configChanged) {
            return restartPreview(trigger)
        }
        if (previewRuntimeService.isServerRunning() && !hasKnownConfigFingerprint) {
            syncConfigFingerprint(projectPath)
        }
        val existingPreviewUrl = dependencies.previewPaneCoordinator.currentUrl(projectId)
        if (previewRuntimeService.isServerRunning() && existingPreviewUrl != null) {
            return ActivationResult(
                success = true,
                previewUrl = existingPreviewUrl,
                message = "Preview already running.",
            )
        }

        val strictPreflightOnChange = strictPreflightOnDependencyChangeEnabled() && configChanged
        val activationResult = dependencies.activationService.activate(
            projectId = projectId,
            projectPath = projectPath,
            startupOutput = dependencies.startupOutputProvider.startupOutput(project, trigger),
            featureFlags = dependencies.featureFlagPolicyService.current(),
            strictPreflightOnChange = strictPreflightOnChange,
            allowDependencyBypass = allowDependencyBypass,
            retryDependencySetup = retryDependencySetup,
        )
        if (activationResult.success) {
            syncConfigFingerprint(projectPath)
        }
        return activationResult
    }

    /**
     * Starts preview on a pooled thread and dispatches completion on the UI thread.
     *
     * Falls back to synchronous execution when no IntelliJ application is available (unit tests).
     */
    fun startPreviewAsync(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        runPreviewOperationAsync(
            operation = { startPreview(trigger) },
            onComplete = onComplete,
        )
    }

    /**
     * Async variant of [retryDependencySetup].
     */
    fun retryDependencySetupAsync(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        runPreviewOperationAsync(
            operation = { retryDependencySetup(trigger) },
            onComplete = onComplete,
        )
    }

    /**
     * Async variant of [startPreviewAnyway].
     */
    fun startPreviewAnywayAsync(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        runPreviewOperationAsync(
            operation = { startPreviewAnyway(trigger) },
            onComplete = onComplete,
        )
    }

    /**
     * Stops active runtime instance for this project.
     */
    @Synchronized
    fun stopPreview(): Boolean {
        clearVerifiedPreviewRouteCache()
        clearTopicMutationConfigVerification()
        return previewRuntimeService.stopServer()
    }

    /**
     * Returns `true` when runtime is currently active for this project.
     */
    fun isRuntimeRunning(): Boolean = previewRuntimeService.isServerRunning()

    /**
     * Restarts active runtime flow for this project.
     *
     * If runtime is not currently running, this method starts preview instead.
     */
    @Synchronized
    fun restartPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        val projectPath = project.basePath
            ?: return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Project base path is not available for runtime restart.",
            )
        val projectId = project.locationHash
        val previousRoute = dependencies.previewPaneCoordinator.currentState(projectId)?.currentRoute
        if (previewRuntimeService.isServerRunning()) {
            previewRuntimeService.stopServer()
        }
        val restarted = startPreview(trigger)
        if (!restarted.success) {
            return restarted
        }

        if (previousRoute != null && previousRoute != "/") {
            dependencies.previewPaneCoordinator.navigate(projectId, previousRoute)
        }

        syncConfigFingerprint(projectPath)
        return restarted.copy(previewUrl = currentPreviewUrl().orEmpty())
    }

    /**
     * Restarts preview on a pooled thread and dispatches completion on the UI thread.
     *
     * Falls back to synchronous execution when no IntelliJ application is available (unit tests).
     */
    fun restartPreviewAsync(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        runPreviewOperationAsync(
            operation = { restartPreview(trigger) },
            onComplete = onComplete,
        )
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
        return buildPreviewRouteIntent(
            selectedPath = selectedPath,
            source = PreviewRouteIntentSource.DIRECT_NAVIGATION,
        )?.targetUrl
    }

    /**
     * Builds an instance-aware route intent for an editor-selected path.
     *
     * This method updates preview pane route state when mapping succeeds.
     */
    fun buildPreviewRouteIntent(
        selectedPath: String,
        source: PreviewRouteIntentSource,
    ): PreviewRouteIntent? {
        if (!isRuntimeRunning()) {
            return null
        }

        val scope = resolveActiveRuntimeScope()
        val routeEntry = resolvePreviewRouteEntry(selectedPath, scope) ?: return null
        return PreviewRouteIntent(
            selectedPath = selectedPath,
            route = routeEntry.route,
            targetUrl = routeEntry.targetUrl,
            docsDirPath = scope.docsDirPath?.toString()?.replace('\\', '/'),
            configPath = scope.configPath?.toString()?.replace('\\', '/'),
            dirtyLivereloadMode = usesDirtyLivereloadServeMode(),
            source = source,
        )
    }

    /**
     * Dispatches preview loading for a selected file path through runtime-owned intent orchestration.
     *
     * Browser callers provide only a load sink callback; readiness and stale-request cancellation are
     * coordinated by this service.
     */
    fun dispatchPreviewForSelectedFile(
        selectedPath: String,
        source: PreviewRouteIntentSource,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean = false,
        onRouteUnavailable: ((String) -> Unit)? = null,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)? = null,
    ): Boolean {
        val intent = buildPreviewRouteIntent(selectedPath, source) ?: return false
        return dispatchPreviewIntent(
            intent = intent,
            loadUrl = loadUrl,
            forceReload = forceReload,
            onRouteUnavailable = onRouteUnavailable,
            onStateChanged = onStateChanged,
        )
    }

    /**
     * Dispatches preview loading for a selected file path with retry fallback for transient
     * intent-build and route-readiness races.
     */
    fun dispatchPreviewForSelectedFileWithRetry(
        selectedPath: String,
        source: PreviewRouteIntentSource,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean = false,
        retryAttempts: Int = DEFAULT_PREVIEW_DISPATCH_RETRY_ATTEMPTS,
        retryDelayMillis: Long = DEFAULT_PREVIEW_DISPATCH_RETRY_DELAY_MS,
        shouldRetry: () -> Boolean = { true },
        onRouteUnavailable: ((String) -> Unit)? = null,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)? = null,
    ): Boolean {
        return dispatchPreviewForSelectedFileWithRetryAttempt(
            selectedPath = selectedPath,
            source = source,
            loadUrl = loadUrl,
            forceReload = forceReload,
            remainingRetries = retryAttempts.coerceAtLeast(0),
            retryDelayMillis = retryDelayMillis.coerceAtLeast(0L),
            shouldRetry = shouldRetry,
            onRouteUnavailable = onRouteUnavailable,
            onStateChanged = onStateChanged,
        )
    }

    /**
     * Dispatches an already-built route intent with readiness guarding and stale-request suppression.
     */
    fun dispatchPreviewIntent(
        intent: PreviewRouteIntent,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean = false,
        onRouteUnavailable: ((String) -> Unit)? = null,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)? = null,
    ): Boolean {
        if (project.isDisposed || !isRuntimeRunning()) {
            return false
        }

        val generation = previewRouteIntentGeneration.incrementAndGet()
        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = intent.targetUrl,
            isRequestCurrent = { previewRouteIntentGeneration.get() == generation },
            isRuntimeRunning = ::isRuntimeRunning,
            loadUrl = { url, routeForceReload ->
                loadUrl(url, forceReload || routeForceReload)
            },
            dirtyLivereloadMode = intent.dirtyLivereloadMode,
            onDirtyLivereloadRouteMiss = if (intent.dirtyLivereloadMode) {
                { nudgeDirtyLivereloadReload() }
            } else {
                null
            },
            onStateChanged = onStateChanged,
            onRouteUnavailable = onRouteUnavailable,
        )
        return true
    }

    /**
     * Returns `true` when the path is a markdown file within the currently served docs scope.
     */
    fun isPreviewEligibleMarkdownPath(selectedPath: String): Boolean {
        if (!isMarkdownPath(selectedPath)) {
            return false
        }
        val scope = resolveActiveRuntimeScope()
        if (scope.configPath == null) {
            return false
        }
        return resolveDocsRelativeMarkdownPath(selectedPath, scope) != null
    }

    /**
     * Returns the config path currently associated with the active runtime command, when available.
     */
    fun activeRuntimeConfigPath(): String? {
        val projectRoot = project.basePath
            ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
        val command = dependencies.processManager
            .diagnostics(project.locationHash)
            ?.command
            .orEmpty()
        val configPath = resolveWatchedConfigPath(command, projectRoot) ?: return null
        return configPath.toAbsolutePath().normalize().toString().replace('\\', '/')
    }

    /**
     * Signals that a filesystem mutation affecting docs/nav has been committed.
     *
     * The latest parsed config nav-state is supplied by topic-tree UI flows and persisted so each
     * mutation can decide whether a full preview restart is required.
     *
     * Each invocation is treated as a mutation batch boundary and rebuilds the verified preview
     * URL cache from disk before any follow-up navigation.
     */
    fun onTopicMutationCommitted(navPresent: Boolean = navPresentFromParsedConfigState): Boolean {
        navPresentFromParsedConfigState = navPresent
        val browserService = runCatching {
            project.getService(MkDocsPreviewBrowserService::class.java)
        }.getOrNull()
        browserService?.resetLastLoadedUrl()
        topicMutationConfigVerificationPending = true

        if (isRuntimeRunning() && navPresentFromParsedConfigState) {
            val restarted = restartPreview(PreviewStartTrigger.ACTION)
            rebuildVerifiedPreviewRouteCacheFromDisk()
            return restarted.success
        }

        rebuildVerifiedPreviewRouteCacheFromDisk()
        if (!isRuntimeRunning() || !usesDirtyLivereloadServeMode()) {
            return false
        }
        return nudgeDirtyLivereloadReload()
    }

    /**
     * Returns true when the active MkDocs runtime command is using `--livereload --dirty`.
     */
    fun usesDirtyLivereloadServeMode(): Boolean {
        val command = dependencies.processManager
            .diagnostics(project.locationHash)
            ?.command
            .orEmpty()
        if (command.isEmpty()) {
            return false
        }
        return command.contains("--livereload") && command.contains("--dirty")
    }

    /**
     * Nudges MkDocs livereload watcher by touching the active served config file.
     *
     * This is used to unblock delayed route publication under `--livereload --dirty` without
     * restarting the runtime process.
     */
    fun nudgeDirtyLivereloadReload(): Boolean {
        val diagnostics = dependencies.processManager.diagnostics(project.locationHash) ?: return false
        val command = diagnostics.command
        if (command.isEmpty() || !command.contains("--livereload") || !command.contains("--dirty")) {
            return false
        }

        val projectRoot = project.basePath
            ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
        val configPath = resolveWatchedConfigPath(command, projectRoot)
            ?: project.basePath?.let(::resolveConfigPath)
            ?: return false
        return touchConfigFile(configPath)
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

    private fun strictPreflightOnDependencyChangeEnabled(): Boolean {
        val settingsService = runCatching {
            project.getService(AuthordPreviewSettingsService::class.java)
        }.getOrNull()
        return settingsService?.strictPreflightOnDependencyChange == true
    }

    /**
     * Disposes project runtime resources safely.
     */
    override fun dispose() {
        previewRuntimeService.stopServer()
        lastConfigFingerprint = null
        clearVerifiedPreviewRouteCache()
        clearTopicMutationConfigVerification()
    }

    private fun runPreviewOperationAsync(
        operation: () -> ActivationResult,
        onComplete: (ActivationResult) -> Unit,
    ) {
        val application = ApplicationManager.getApplication()
        if (application == null) {
            onComplete(operation())
            return
        }

        application.executeOnPooledThread {
            val result = operation()
            application.invokeLater(
                {
                    if (!project.isDisposed) {
                        onComplete(result)
                    }
                },
                ModalityState.any(),
            )
        }
    }

    private fun dispatchPreviewForSelectedFileWithRetryAttempt(
        selectedPath: String,
        source: PreviewRouteIntentSource,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean,
        remainingRetries: Int,
        retryDelayMillis: Long,
        shouldRetry: () -> Boolean,
        onRouteUnavailable: ((String) -> Unit)?,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)?,
    ): Boolean {
        if (project.isDisposed || !isRuntimeRunning() || !shouldRetry()) {
            return false
        }
        if (!isSelectedFileReadyForPreview(selectedPath, source)) {
            if (source == PreviewRouteIntentSource.TOPIC_MUTATION && remainingRetries > 0 && shouldRetry()) {
                refreshPreviewStateForRetry(source)
                return scheduleTopicMutationVerificationHold(
                    selectedPath = selectedPath,
                    source = source,
                    loadUrl = loadUrl,
                    forceReload = forceReload,
                    remainingRetries = remainingRetries,
                    retryDelayMillis = retryDelayMillis,
                    shouldRetry = shouldRetry,
                    onRouteUnavailable = onRouteUnavailable,
                    onStateChanged = onStateChanged,
                )
            }
            if (remainingRetries <= 0 || !shouldRetry()) {
                return false
            }
            refreshPreviewStateForRetry(source)
            return schedulePreviewDispatchRetry(
                selectedPath = selectedPath,
                source = source,
                loadUrl = loadUrl,
                forceReload = forceReload,
                remainingRetries = remainingRetries - 1,
                retryDelayMillis = retryDelayMillis,
                shouldRetry = shouldRetry,
                onRouteUnavailable = onRouteUnavailable,
                onStateChanged = onStateChanged,
            )
        }

        if (source == PreviewRouteIntentSource.TOPIC_MUTATION) {
            val routeEntry = lookupVerifiedPreviewRouteEntry(selectedPath)
                ?: run {
                    rebuildVerifiedPreviewRouteCacheFromDisk()
                    lookupVerifiedPreviewRouteEntry(selectedPath)
                }
            if (routeEntry != null) {
                dependencies.previewPaneCoordinator.navigate(project.locationHash, routeEntry.route)
                loadUrl(routeEntry.targetUrl, true)
                return true
            }
        }

        val applied = dispatchPreviewForSelectedFile(
            selectedPath = selectedPath,
            source = source,
            loadUrl = loadUrl,
            forceReload = forceReload,
            onRouteUnavailable = { message ->
                if (remainingRetries > 0 && shouldRetry()) {
                    refreshPreviewStateForRetry(source)
                    schedulePreviewDispatchRetry(
                        selectedPath = selectedPath,
                        source = source,
                        loadUrl = loadUrl,
                        forceReload = forceReload,
                        remainingRetries = remainingRetries - 1,
                        retryDelayMillis = retryDelayMillis,
                        shouldRetry = shouldRetry,
                        onRouteUnavailable = onRouteUnavailable,
                        onStateChanged = onStateChanged,
                    )
                } else {
                    onRouteUnavailable?.invoke(message)
                }
            },
            onStateChanged = onStateChanged,
        )
        if (applied) {
            return true
        }
        if (remainingRetries <= 0 || !shouldRetry()) {
            return false
        }

        refreshPreviewStateForRetry(source)
        return schedulePreviewDispatchRetry(
            selectedPath = selectedPath,
            source = source,
            loadUrl = loadUrl,
            forceReload = forceReload,
            remainingRetries = remainingRetries - 1,
            retryDelayMillis = retryDelayMillis,
            shouldRetry = shouldRetry,
            onRouteUnavailable = onRouteUnavailable,
            onStateChanged = onStateChanged,
        )
    }

    private fun scheduleTopicMutationVerificationHold(
        selectedPath: String,
        source: PreviewRouteIntentSource,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean,
        remainingRetries: Int,
        retryDelayMillis: Long,
        shouldRetry: () -> Boolean,
        onRouteUnavailable: ((String) -> Unit)?,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)?,
    ): Boolean {
        val app = ApplicationManager.getApplication()
            ?: return schedulePreviewDispatchRetry(
                selectedPath = selectedPath,
                source = source,
                loadUrl = loadUrl,
                forceReload = forceReload,
                remainingRetries = (remainingRetries - 1).coerceAtLeast(0),
                retryDelayMillis = retryDelayMillis,
                shouldRetry = shouldRetry,
                onRouteUnavailable = onRouteUnavailable,
                onStateChanged = onStateChanged,
            )
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                app.invokeLater(
                    {
                        if (project.isDisposed || !shouldRetry()) {
                            return@invokeLater
                        }
                        dispatchPreviewForSelectedFileWithRetryAttempt(
                            selectedPath = selectedPath,
                            source = source,
                            loadUrl = loadUrl,
                            forceReload = forceReload,
                            remainingRetries = remainingRetries,
                            retryDelayMillis = retryDelayMillis,
                            shouldRetry = shouldRetry,
                            onRouteUnavailable = onRouteUnavailable,
                            onStateChanged = onStateChanged,
                        )
                    },
                    ModalityState.any(),
                )
            },
            TOPIC_MUTATION_VERIFICATION_HOLD_DELAY_MS,
            TimeUnit.MILLISECONDS,
        )
        return true
    }

    private fun refreshPreviewStateForRetry(source: PreviewRouteIntentSource) {
        if (source != PreviewRouteIntentSource.TOPIC_MUTATION) {
            return
        }
        rebuildVerifiedPreviewRouteCacheFromDisk()
        if (isRuntimeRunning() && usesDirtyLivereloadServeMode()) {
            nudgeDirtyLivereloadReload()
        }
    }

    private fun schedulePreviewDispatchRetry(
        selectedPath: String,
        source: PreviewRouteIntentSource,
        loadUrl: (url: String, forceReload: Boolean) -> Unit,
        forceReload: Boolean,
        remainingRetries: Int,
        retryDelayMillis: Long,
        shouldRetry: () -> Boolean,
        onRouteUnavailable: ((String) -> Unit)?,
        onStateChanged: ((PreviewRouteFlowState, String) -> Unit)?,
    ): Boolean {
        if (source == PreviewRouteIntentSource.TOPIC_MUTATION) {
            val app = ApplicationManager.getApplication()
            if (app == null) {
                return dispatchPreviewForSelectedFileWithRetryAttempt(
                    selectedPath = selectedPath,
                    source = source,
                    loadUrl = loadUrl,
                    forceReload = forceReload,
                    remainingRetries = remainingRetries,
                    retryDelayMillis = 0L,
                    shouldRetry = shouldRetry,
                    onRouteUnavailable = onRouteUnavailable,
                    onStateChanged = onStateChanged,
                )
            }
            app.invokeLater(
                {
                    if (project.isDisposed) {
                        return@invokeLater
                    }
                    dispatchPreviewForSelectedFileWithRetryAttempt(
                        selectedPath = selectedPath,
                        source = source,
                        loadUrl = loadUrl,
                        forceReload = forceReload,
                        remainingRetries = remainingRetries,
                        retryDelayMillis = 0L,
                        shouldRetry = shouldRetry,
                        onRouteUnavailable = onRouteUnavailable,
                        onStateChanged = onStateChanged,
                    )
                },
                ModalityState.any(),
            )
            return true
        }

        val delayMillis = retryDelayMillis.coerceAtLeast(0L)
        val app = ApplicationManager.getApplication()
        if (app == null) {
            if (!sleepPreviewDispatchRetry(delayMillis)) {
                return false
            }
            return dispatchPreviewForSelectedFileWithRetryAttempt(
                selectedPath = selectedPath,
                source = source,
                loadUrl = loadUrl,
                forceReload = forceReload,
                remainingRetries = remainingRetries,
                retryDelayMillis = delayMillis,
                shouldRetry = shouldRetry,
                onRouteUnavailable = onRouteUnavailable,
                onStateChanged = onStateChanged,
            )
        }

        app.executeOnPooledThread {
            if (!sleepPreviewDispatchRetry(delayMillis)) {
                return@executeOnPooledThread
            }
            app.invokeLater(
                {
                    if (project.isDisposed) {
                        return@invokeLater
                    }
                    dispatchPreviewForSelectedFileWithRetryAttempt(
                        selectedPath = selectedPath,
                        source = source,
                        loadUrl = loadUrl,
                        forceReload = forceReload,
                        remainingRetries = remainingRetries,
                        retryDelayMillis = delayMillis,
                        shouldRetry = shouldRetry,
                        onRouteUnavailable = onRouteUnavailable,
                        onStateChanged = onStateChanged,
                    )
                },
                ModalityState.any(),
            )
        }
        return true
    }

    private fun sleepPreviewDispatchRetry(delayMillis: Long): Boolean {
        if (delayMillis <= 0L) {
            return true
        }
        return try {
            Thread.sleep(delayMillis)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun isSelectedFileReadyForPreview(
        selectedPath: String,
        source: PreviewRouteIntentSource,
    ): Boolean {
        if (source != PreviewRouteIntentSource.TOPIC_MUTATION) {
            return true
        }
        val normalizedPath = normalizeSelectedPath(selectedPath) ?: return false
        val markdownReady = runCatching { Files.isRegularFile(normalizedPath) }.getOrDefault(false)
        return markdownReady && isTopicMutationConfigReadyForPreview()
    }

    private fun isTopicMutationConfigReadyForPreview(): Boolean {
        val configPath = resolveActiveRuntimeScope().configPath ?: return false
        if (!runCatching { Files.isRegularFile(configPath) }.getOrDefault(false)) {
            return false
        }
        val fingerprint = createConfigFingerprint(configPath) ?: return false
        if (topicMutationConfigVerificationPending) {
            if (lastVerifiedTopicMutationConfigFingerprint != null && lastVerifiedTopicMutationConfigFingerprint == fingerprint) {
                return false
            }
            lastVerifiedTopicMutationConfigFingerprint = fingerprint
            topicMutationConfigVerificationPending = false
            return true
        }
        if (lastVerifiedTopicMutationConfigFingerprint != fingerprint) {
            lastVerifiedTopicMutationConfigFingerprint = fingerprint
        }
        return true
    }

    private fun clearTopicMutationConfigVerification() {
        topicMutationConfigVerificationPending = false
        lastVerifiedTopicMutationConfigFingerprint = null
        navPresentFromParsedConfigState = false
    }

    private fun resolvePreviewRouteEntry(
        selectedPath: String,
        scope: RuntimeScope,
    ): VerifiedPreviewRouteEntry? {
        val cached = lookupVerifiedPreviewRouteEntry(selectedPath)
        if (cached != null) {
            dependencies.previewPaneCoordinator.navigate(project.locationHash, cached.route)
            return cached
        }

        val docsRelativePath = resolveDocsRelativeMarkdownPath(selectedPath, scope) ?: return null
        val navigationResult = dependencies.navigationCoordinator.onFileSelected(
            project.locationHash,
            "docs/$docsRelativePath",
        )
        if (!navigationResult.applied) {
            return null
        }

        val targetUrl = currentPreviewUrl()?.takeIf { it.isNotBlank() } ?: return null
        val resolved = VerifiedPreviewRouteEntry(
            route = navigationResult.route,
            targetUrl = targetUrl,
        )
        cacheVerifiedPreviewRouteEntry(selectedPath, resolved)
        return resolved
    }

    private fun rebuildVerifiedPreviewRouteCacheFromDisk() {
        val baseUrl = ensureVerifiedPreviewRouteCacheBaseUrl() ?: return
        val docsDirPath = resolveActiveRuntimeScope().docsDirPath
        if (docsDirPath == null || !runCatching { Files.isDirectory(docsDirPath) }.getOrDefault(false)) {
            verifiedPreviewRouteCache = emptyMap()
            return
        }

        val rebuilt = runCatching {
            val entries = linkedMapOf<String, VerifiedPreviewRouteEntry>()
            Files.walk(docsDirPath).use { paths ->
                paths.forEach { candidate ->
                    if (!runCatching { Files.isRegularFile(candidate) }.getOrDefault(false)) {
                        return@forEach
                    }
                    val relativePath = runCatching {
                        docsDirPath.relativize(candidate).toString().replace('\\', '/')
                    }.getOrNull() ?: return@forEach
                    val markdownRelative = normalizeMarkdownRelativePath(relativePath) ?: return@forEach
                    val route = routeMappingService.mapToRoute("docs/${markdownRelative.trimStart('/')}") ?: return@forEach
                    entries[previewRouteCacheKey(candidate)] = VerifiedPreviewRouteEntry(
                        route = route,
                        targetUrl = composePreviewTargetUrl(baseUrl, route),
                    )
                }
            }
            entries
        }.getOrElse {
            emptyMap()
        }
        verifiedPreviewRouteCache = rebuilt
    }

    private fun lookupVerifiedPreviewRouteEntry(selectedPath: String): VerifiedPreviewRouteEntry? {
        ensureVerifiedPreviewRouteCacheBaseUrl() ?: return null
        val key = previewRouteCacheKey(selectedPath) ?: return null
        return verifiedPreviewRouteCache[key]
    }

    private fun cacheVerifiedPreviewRouteEntry(
        selectedPath: String,
        entry: VerifiedPreviewRouteEntry,
    ) {
        ensureVerifiedPreviewRouteCacheBaseUrl() ?: return
        val key = previewRouteCacheKey(selectedPath) ?: return
        val updated = LinkedHashMap(verifiedPreviewRouteCache)
        updated[key] = entry
        verifiedPreviewRouteCache = updated
    }

    private fun ensureVerifiedPreviewRouteCacheBaseUrl(): String? {
        val baseUrl = currentPreviewBaseUrl()?.takeIf { it.isNotBlank() }
        if (baseUrl == null) {
            clearVerifiedPreviewRouteCache()
            return null
        }
        if (verifiedPreviewRouteCacheBaseUrl != baseUrl) {
            verifiedPreviewRouteCache = emptyMap()
            verifiedPreviewRouteCacheBaseUrl = baseUrl
        }
        return baseUrl
    }

    private fun currentPreviewBaseUrl(): String? {
        return dependencies.previewPaneCoordinator.currentState(project.locationHash)
            ?.baseUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun clearVerifiedPreviewRouteCache() {
        verifiedPreviewRouteCache = emptyMap()
        verifiedPreviewRouteCacheBaseUrl = null
    }

    private fun composePreviewTargetUrl(baseUrl: String, route: String): String {
        return if (route == "/") {
            baseUrl
        } else {
            "${baseUrl.trimEnd('/')}$route"
        }
    }

    private fun previewRouteCacheKey(selectedPath: String): String? {
        val normalizedPath = normalizeSelectedPath(selectedPath) ?: return null
        return previewRouteCacheKey(normalizedPath)
    }

    private fun previewRouteCacheKey(path: Path): String {
        val normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/')
        return if (SystemInfoRt.isFileSystemCaseSensitive) {
            normalized
        } else {
            normalized.lowercase()
        }
    }

    private fun hasConfigChanged(projectPath: String): Boolean {
        val current = currentConfigFingerprint(projectPath)
        return current != lastConfigFingerprint
    }

    private fun syncConfigFingerprint(projectPath: String) {
        lastConfigFingerprint = currentConfigFingerprint(projectPath)
    }

    private fun currentConfigFingerprint(projectPath: String): String? {
        val configPath = resolveConfigPath(projectPath) ?: return null
        return createConfigFingerprint(configPath)
    }

    private fun createConfigFingerprint(configPath: Path): String? {
        val normalizedPath = configPath.toAbsolutePath().normalize()
        val content = runCatching { Files.readString(normalizedPath) }.getOrNull() ?: return null
        return "${normalizedPath}::${content.hashCode()}"
    }

    private fun resolveConfigPath(projectPath: String): Path? {
        val root = runCatching { Path.of(projectPath).toAbsolutePath().normalize() }.getOrNull() ?: return null
        return findMkdocsConfig(root)
    }

    private fun resolveWatchedConfigPath(command: List<String>, projectRoot: Path?): Path? {
        val flagIndex = command.indexOf("-f")
        val candidate = command.getOrNull(flagIndex + 1)?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }
        val configPath = runCatching { Path.of(candidate) }.getOrNull() ?: return null
        val resolved = when {
            configPath.isAbsolute -> configPath
            projectRoot != null -> projectRoot.resolve(configPath)
            else -> configPath.toAbsolutePath()
        }
        return runCatching { resolved.normalize() }.getOrNull()
    }

    private fun touchConfigFile(path: Path): Boolean {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false
        }

        return runCatching {
            Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()))
            true
        }.getOrDefault(false)
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

    private fun resolveActiveRuntimeScope(): RuntimeScope {
        val projectRoot = project.basePath
            ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
        val command = dependencies.processManager
            .diagnostics(project.locationHash)
            ?.command
            .orEmpty()
        val configPath = resolveWatchedConfigPath(command, projectRoot)
            ?: project.basePath?.let(::resolveConfigPath)
        val docsDirPath = configPath?.let(::resolveDocsDirPathFromConfig)
            ?: projectRoot?.resolve("docs")?.normalize()
        return RuntimeScope(
            configPath = configPath,
            docsDirPath = docsDirPath,
        )
    }

    private fun resolveDocsDirPathFromConfig(configPath: Path): Path {
        val docsDir = readDocsDirValue(configPath) ?: "docs"
        val configuredPath = runCatching { Path.of(docsDir) }.getOrNull()
        val absolute = if (configuredPath != null && configuredPath.isAbsolute) {
            configuredPath
        } else {
            configPath.parent.resolve(docsDir)
        }
        return absolute.toAbsolutePath().normalize()
    }

    private fun readDocsDirValue(configPath: Path): String? {
        if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
            return null
        }

        return runCatching {
            Files.readAllLines(configPath)
                .asSequence()
                .map { line ->
                    docsDirLineRegex.find(line)?.groupValues?.getOrNull(1)
                }
                .mapNotNull { raw -> raw?.let(::parseYamlScalar) }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseYamlScalar(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1).replace("''", "'").trim()
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
        }
        return trimmed.substringBefore('#').trim()
    }

    private fun resolveDocsRelativeMarkdownPath(
        selectedPath: String,
        scope: RuntimeScope,
    ): String? {
        val docsDirPath = scope.docsDirPath ?: return null
        val selectedAbsolute = normalizeSelectedPath(selectedPath) ?: return null
        if (!selectedAbsolute.startsWith(docsDirPath)) {
            return null
        }

        val relativePath = runCatching {
            docsDirPath.relativize(selectedAbsolute).toString().replace('\\', '/')
        }.getOrNull() ?: return null

        val markdownRelative = normalizeMarkdownRelativePath(relativePath) ?: return null
        return markdownRelative.trimStart('/').takeIf { it.isNotBlank() }
    }

    private fun normalizeMarkdownRelativePath(relativePath: String): String? {
        return when {
            relativePath.endsWith(".md", ignoreCase = true) -> relativePath
            relativePath.endsWith(".markdown", ignoreCase = true) -> {
                relativePath.replace(Regex("\\.markdown$", RegexOption.IGNORE_CASE), ".md")
            }
            else -> null
        }
    }

    private fun normalizeSelectedPath(selectedPath: String): Path? {
        val trimmed = selectedPath.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val candidate = runCatching { Path.of(trimmed) }.getOrNull() ?: return null
        if (candidate.isAbsolute) {
            return candidate.toAbsolutePath().normalize()
        }
        val projectRoot = project.basePath
            ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
            ?: return candidate.toAbsolutePath().normalize()
        return projectRoot.resolve(candidate).normalize()
    }

    private data class RuntimeScope(
        val configPath: Path?,
        val docsDirPath: Path?,
    )

    companion object {
        private val docsDirLineRegex = Regex("""^\s*docs_dir\s*:\s*(.+?)\s*(?:#.*)?$""")
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
        val KEY: Key<String> = Key.create("authord.startupOutput")
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
