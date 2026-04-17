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
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** API version for plugin runtime integration service. */
const val PLUGIN_RUNTIME_INTEGRATION_API_VERSION: String = "1.0.0"
private const val DEFAULT_PREVIEW_DISPATCH_RETRY_ATTEMPTS: Int = 2
private const val DEFAULT_PREVIEW_DISPATCH_RETRY_DELAY_MS: Long = 1_200L
private const val TOPIC_MUTATION_VERIFICATION_HOLD_DELAY_MS: Long = 150L
private const val MAX_DOCS_ROUTE_SCAN_DEPTH: Int = 20
private const val TOC_ROUTE_READY_CACHE_TTL_MS: Long = 10_000L
private const val MAX_READY_ROUTE_CACHE_ENTRIES: Int = 256

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
    private data class ConfigFingerprintSnapshot(
        val hasKnownFingerprint: Boolean,
        val hasChanged: Boolean,
        val currentFingerprint: String?,
    )

    private data class VerifiedPreviewRouteEntry(
        val route: String,
        val targetUrl: String,
    )

    private data class VerifiedPreviewRouteCacheState(
        val baseUrl: String? = null,
        val entries: Map<String, VerifiedPreviewRouteEntry> = emptyMap(),
    )

    private var dependencies: RuntimeIntegrationDependencies = RuntimeIntegrationDependencies.createDefault()
    private val routeMappingService = RouteMappingService()
    private val siteContextResolver = SiteContextResolver()
    private val previewRuntimeService = MkDocsPreviewService(
        processManagerProvider = { dependencies.processManager },
        projectIdProvider = { project.locationHash },
    )
    private val stateLock = ReentrantLock()
    private var lastConfigFingerprint: String? = null
    private val previewRouteIntentGeneration = AtomicInteger(0)
    private var verifiedPreviewRouteCacheState = VerifiedPreviewRouteCacheState()
    private var topicMutationConfigVerificationPending: Boolean = false
    private var lastVerifiedTopicMutationConfigFingerprint: String? = null
    private var navPresentFromParsedConfigState: Boolean = false
    private val recentlyReadyRouteExpiryByUrl = linkedMapOf<String, Long>()
    private val previewOperationExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "authord-preview-operation-${project.locationHash}").apply { isDaemon = true }
    }
    private val fallbackRetryScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "authord-preview-retry-${project.locationHash}").apply { isDaemon = true }
    }

    private inline fun <T> withStateLock(action: () -> T): T = stateLock.withLock(action)

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
    fun startPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        val application = ApplicationManager.getApplication()
        if (application?.isDispatchThread == true && !application.isUnitTestMode) {
            return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Preview start requested on UI thread; call startPreviewAsync() instead.",
            )
        }
        val projectPath = project.basePath
            ?: return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Project base path is not available for runtime start.",
            )

        val projectId = project.locationHash
        val isServerRunning = previewRuntimeService.isServerRunning()
        val fingerprintSnapshot = currentFingerprintSnapshot(projectPath)
        if (isServerRunning && fingerprintSnapshot.hasKnownFingerprint && fingerprintSnapshot.hasChanged) {
            return restartPreview(trigger)
        }
        if (isServerRunning && !fingerprintSnapshot.hasKnownFingerprint) {
            withStateLock {
                if (lastConfigFingerprint == null) {
                    lastConfigFingerprint = fingerprintSnapshot.currentFingerprint
                }
            }
        }
        val existingPreviewUrl = dependencies.previewPaneCoordinator.currentUrl(projectId)
        if (isServerRunning && existingPreviewUrl != null) {
            return ActivationResult(
                success = true,
                previewUrl = existingPreviewUrl,
                message = "Preview already running.",
            )
        }

        val activationResult = dependencies.activationService.activate(
            projectId = projectId,
            projectPath = projectPath,
            startupOutput = dependencies.startupOutputProvider.startupOutput(project, trigger),
            featureFlags = dependencies.featureFlagPolicyService.current(),
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
     * Starts preview with IDE background-task progress shown in the status bar.
     */
    fun startPreviewWithProgress(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        if (ApplicationManager.getApplication() == null) {
            onComplete(startPreview(trigger))
            return
        }

        runAuthordBackgroundTask(
            project = project,
            title = "Starting Authord Preview",
            canBeCancelled = false,
            operation = { indicator ->
                indicator.isIndeterminate = true
                indicator.text = "Resolving MkDocs runtime..."
                startPreview(trigger)
            },
            onSuccess = { result ->
                if (!project.isDisposed) {
                    onComplete(result)
                }
            },
            onError = { error ->
                if (!project.isDisposed) {
                    onComplete(
                        ActivationResult(
                            success = false,
                            reason = ActivationFailureReason.START_FAILED,
                            message = "Unexpected error: ${error.message ?: "Unknown failure"}",
                        ),
                    )
                }
            },
        )
    }

    /**
     * Stops active runtime instance for this project.
     */
    fun stopPreview(): Boolean {
        clearVerifiedPreviewRouteCache()
        clearReadyRouteCacheForFastPath()
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
    fun restartPreview(trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION): ActivationResult {
        val application = ApplicationManager.getApplication()
        if (application?.isDispatchThread == true && !application.isUnitTestMode) {
            return ActivationResult(
                success = false,
                reason = ActivationFailureReason.START_FAILED,
                message = "Preview restart requested on UI thread; call restartPreviewAsync() instead.",
            )
        }
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
     * Restarts preview with IDE background-task progress shown in the status bar.
     */
    fun restartPreviewWithProgress(
        trigger: PreviewStartTrigger = PreviewStartTrigger.ACTION,
        onComplete: (ActivationResult) -> Unit,
    ) {
        if (ApplicationManager.getApplication() == null) {
            onComplete(restartPreview(trigger))
            return
        }

        runAuthordBackgroundTask(
            project = project,
            title = "Restarting Authord Preview",
            canBeCancelled = false,
            operation = { indicator ->
                indicator.isIndeterminate = true
                indicator.text = "Restarting server..."
                restartPreview(trigger)
            },
            onSuccess = { result ->
                if (!project.isDisposed) {
                    onComplete(result)
                }
            },
            onError = { error ->
                if (!project.isDisposed) {
                    onComplete(
                        ActivationResult(
                            success = false,
                            reason = ActivationFailureReason.START_FAILED,
                            message = "Unexpected restart error: ${error.message ?: "Unknown failure"}",
                        ),
                    )
                }
            },
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
        val normalizedTargetUrl = intent.targetUrl.trim()
        val fastPathHit = intent.source == PreviewRouteIntentSource.TOOL_WINDOW_SELECTION &&
            !forceReload &&
            isKnownReadyRouteForFastPath(normalizedTargetUrl)
        if (fastPathHit) {
            notifyRouteState(onStateChanged, PreviewRouteFlowState.INTENT_ACCEPTED, normalizedTargetUrl)
            notifyRouteState(onStateChanged, PreviewRouteFlowState.ROUTE_READY, normalizedTargetUrl)
            loadUrl(normalizedTargetUrl, forceReload)
            rememberReadyRouteForFastPath(normalizedTargetUrl)
            notifyRouteState(onStateChanged, PreviewRouteFlowState.LOADED, normalizedTargetUrl)
            return true
        }

        val stateBridge: (PreviewRouteFlowState, String) -> Unit = { state, url ->
            if (state == PreviewRouteFlowState.LOADED) {
                rememberReadyRouteForFastPath(url)
            }
            onStateChanged?.invoke(state, url)
        }
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
            onStateChanged = stateBridge,
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
        val configPath = resolveActiveRuntimeScope().configPath ?: return null
        return configPath.toString().replace('\\', '/')
    }

    /**
     * Signals that a filesystem mutation affecting docs/nav has been committed.
     *
     * The latest parsed config nav-state is supplied by topic-tree UI flows and persisted so each
     * mutation can decide whether a stabilization hold is needed before livereload is nudged.
     *
     * Route-cache entries are updated from mutation operation deltas when available, with
     * full-scan fallback when delta application is unsafe.
     */
    fun onTopicMutationCommitted(
        navPresent: Boolean? = null,
        fileOperations: List<TopicFileOperation> = emptyList(),
    ): Boolean {
        clearReadyRouteCacheForFastPath()
        val effectiveNavPresent = withStateLock {
            val resolved = navPresent ?: navPresentFromParsedConfigState
            navPresentFromParsedConfigState = resolved
            topicMutationConfigVerificationPending = resolved
            resolved
        }
        val browserService = runCatching {
            project.getService(MkDocsPreviewBrowserService::class.java)
        }.getOrNull()
        val cacheDeltaApplied = applyTopicMutationRouteCacheDelta(fileOperations)
        if (!cacheDeltaApplied) {
            rebuildVerifiedPreviewRouteCacheFromDisk()
        }
        browserService?.resetLastLoadedUrl()
        if (!isRuntimeRunning() || !usesDirtyLivereloadServeMode()) {
            return false
        }
        if (!effectiveNavPresent) {
            return false
        }
        if (!holdTopicMutationLivereloadNudgeWindow()) {
            return false
        }
        return nudgeDirtyLivereloadReload()
    }

    /**
     * Runs topic-mutation runtime reconciliation off the UI thread and dispatches completion on UI.
     *
     * Falls back to synchronous execution when no IntelliJ application is available (unit tests).
     */
    fun onTopicMutationCommittedAsync(
        navPresent: Boolean? = null,
        fileOperations: List<TopicFileOperation> = emptyList(),
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        val application = ApplicationManager.getApplication()
        if (application == null) {
            val result = onTopicMutationCommitted(navPresent, fileOperations)
            onComplete?.invoke(result)
            return
        }

        application.executeOnPooledThread {
            val result = onTopicMutationCommitted(navPresent, fileOperations)
            application.invokeLater(
                {
                    if (!project.isDisposed) {
                        onComplete?.invoke(result)
                    }
                },
                ModalityState.any(),
            )
        }
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

    private fun holdTopicMutationLivereloadNudgeWindow(): Boolean {
        return try {
            TimeUnit.MILLISECONDS.sleep(TOPIC_MUTATION_VERIFICATION_HOLD_DELAY_MS)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
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
        previewRuntimeService.stopServer()
        previewOperationExecutor.shutdownNow()
        fallbackRetryScheduler.shutdownNow()
        project.basePath?.takeIf { it.isNotBlank() }?.let { projectPath ->
            dependencies.activationService.disposeProjectResources(projectPath)
        }
        withStateLock {
            lastConfigFingerprint = null
        }
        clearVerifiedPreviewRouteCache()
        clearReadyRouteCacheForFastPath()
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

        try {
            previewOperationExecutor.execute {
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
        } catch (_: RejectedExecutionException) {
            if (!project.isDisposed) {
                onComplete(operation())
            }
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
                            remainingRetries = (remainingRetries - 1).coerceAtLeast(0),
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
        val navPresent = withStateLock { navPresentFromParsedConfigState }
        if (navPresent && isRuntimeRunning() && usesDirtyLivereloadServeMode()) {
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
        val delayMillis = if (source == PreviewRouteIntentSource.TOPIC_MUTATION) {
            0L
        } else {
            retryDelayMillis.coerceAtLeast(0L)
        }
        val app = ApplicationManager.getApplication()
        if (app != null) {
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
                                retryDelayMillis = delayMillis,
                                shouldRetry = shouldRetry,
                                onRouteUnavailable = onRouteUnavailable,
                                onStateChanged = onStateChanged,
                            )
                        },
                        ModalityState.any(),
                    )
                },
                delayMillis,
                TimeUnit.MILLISECONDS,
            )
            return true
        }

        return try {
            fallbackRetryScheduler.schedule(
                {
                    if (!project.isDisposed && shouldRetry()) {
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
                    }
                },
                delayMillis,
                TimeUnit.MILLISECONDS,
            )
            true
        } catch (_: RejectedExecutionException) {
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
        return withStateLock {
            if (topicMutationConfigVerificationPending) {
                if (lastVerifiedTopicMutationConfigFingerprint != null &&
                    lastVerifiedTopicMutationConfigFingerprint == fingerprint
                ) {
                    return@withStateLock false
                }
                lastVerifiedTopicMutationConfigFingerprint = fingerprint
                topicMutationConfigVerificationPending = false
                return@withStateLock true
            }
            if (lastVerifiedTopicMutationConfigFingerprint != fingerprint) {
                lastVerifiedTopicMutationConfigFingerprint = fingerprint
            }
            return true
        }
    }

    private fun clearTopicMutationConfigVerification() {
        withStateLock {
            topicMutationConfigVerificationPending = false
            lastVerifiedTopicMutationConfigFingerprint = null
            navPresentFromParsedConfigState = false
        }
    }

    private fun applyTopicMutationRouteCacheDelta(fileOperations: List<TopicFileOperation>): Boolean {
        if (fileOperations.isEmpty()) {
            return true
        }
        val baseUrl = ensureVerifiedPreviewRouteCacheBaseUrl() ?: return true
        val scope = resolveActiveRuntimeScope()
        val docsDirPath = scope.docsDirPath
        if (docsDirPath == null || !runCatching { Files.isDirectory(docsDirPath) }.getOrDefault(false)) {
            return false
        }
        val orderedOperations = fileOperations
            .withIndex()
            .sortedWith(
                compareBy<IndexedValue<TopicFileOperation>>(
                    { indexed -> previewRouteCacheDeltaPriority(indexed.value.kind) },
                    { indexed -> indexed.index },
                ),
            )
            .map { indexed -> indexed.value }
        val updatedEntries = withStateLock {
            val state = verifiedPreviewRouteCacheState
            if (state.baseUrl != baseUrl) {
                null
            } else {
                LinkedHashMap(state.entries)
            }
        } ?: run {
            return false
        }
        for (operation in orderedOperations) {
            val applied = applyTopicMutationRouteCacheOperation(
                operation = operation,
                docsDirPath = docsDirPath,
                baseUrl = baseUrl,
                useDirectoryUrls = scope.useDirectoryUrls,
                entries = updatedEntries,
            )
            if (!applied) {
                return false
            }
        }
        val committed = withStateLock {
            val state = verifiedPreviewRouteCacheState
            if (state.baseUrl != baseUrl) {
                false
            } else {
                verifiedPreviewRouteCacheState = state.copy(entries = updatedEntries)
                true
            }
        }
        return committed
    }

    private fun applyTopicMutationRouteCacheOperation(
        operation: TopicFileOperation,
        docsDirPath: Path,
        baseUrl: String,
        useDirectoryUrls: Boolean,
        entries: MutableMap<String, VerifiedPreviewRouteEntry>,
    ): Boolean {
        when (operation.kind) {
            TopicFileOperationKind.REWRITE_LINKS -> return true

            TopicFileOperationKind.DELETE -> {
                val sourceRelative = normalizeOperationRelativePath(operation.sourcePath) ?: return false
                val sourceAbsolute = resolveOperationPathWithinDocs(sourceRelative, docsDirPath) ?: return false
                val sourceMarkdown = normalizeMarkdownRelativePath(sourceRelative)
                    ?.trimStart('/')
                    ?.takeIf { it.isNotBlank() }
                if (sourceMarkdown == null) {
                    return true
                }
                entries.remove(previewRouteCacheKey(sourceAbsolute))
                return true
            }

            TopicFileOperationKind.CREATE -> {
                val sourceRelative = normalizeOperationRelativePath(operation.sourcePath) ?: return false
                val sourceAbsolute = resolveOperationPathWithinDocs(sourceRelative, docsDirPath) ?: return false
                val sourceMarkdown = normalizeMarkdownRelativePath(sourceRelative)
                    ?.trimStart('/')
                    ?.takeIf { it.isNotBlank() }
                if (sourceMarkdown == null) {
                    return true
                }
                val entry = composeVerifiedPreviewRouteEntry(
                    markdownRelativePath = sourceMarkdown,
                    baseUrl = baseUrl,
                    useDirectoryUrls = useDirectoryUrls,
                ) ?: return false
                entries[previewRouteCacheKey(sourceAbsolute)] = entry
                return true
            }

            TopicFileOperationKind.MOVE,
            TopicFileOperationKind.RENAME,
            -> {
                val sourceRelative = normalizeOperationRelativePath(operation.sourcePath) ?: return false
                val targetRelative = normalizeOperationRelativePath(operation.targetPath) ?: return false
                val sourceAbsolute = resolveOperationPathWithinDocs(sourceRelative, docsDirPath) ?: return false
                val targetAbsolute = resolveOperationPathWithinDocs(targetRelative, docsDirPath) ?: return false
                val sourceMarkdown = normalizeMarkdownRelativePath(sourceRelative)
                    ?.trimStart('/')
                    ?.takeIf { it.isNotBlank() }
                val targetMarkdown = normalizeMarkdownRelativePath(targetRelative)
                    ?.trimStart('/')
                    ?.takeIf { it.isNotBlank() }
                if (sourceMarkdown == null && targetMarkdown == null) {
                    return true
                }
                if (sourceMarkdown != null) {
                    entries.remove(previewRouteCacheKey(sourceAbsolute))
                }
                if (targetMarkdown == null) {
                    return true
                }
                val entry = composeVerifiedPreviewRouteEntry(
                    markdownRelativePath = targetMarkdown,
                    baseUrl = baseUrl,
                    useDirectoryUrls = useDirectoryUrls,
                ) ?: return false
                entries[previewRouteCacheKey(targetAbsolute)] = entry
                return true
            }
        }
    }

    private fun previewRouteCacheDeltaPriority(kind: TopicFileOperationKind): Int {
        return when (kind) {
            TopicFileOperationKind.MOVE -> 0
            TopicFileOperationKind.RENAME -> 1
            TopicFileOperationKind.DELETE -> 2
            TopicFileOperationKind.CREATE -> 3
            TopicFileOperationKind.REWRITE_LINKS -> 4
        }
    }

    private fun normalizeOperationRelativePath(path: String?): String? {
        return path
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('\\', '/')
            ?.trimStart('/')
            ?.takeIf { it.isNotEmpty() }
    }

    private fun resolveOperationPathWithinDocs(relativePath: String, docsDirPath: Path): Path? {
        val resolved = runCatching { docsDirPath.resolve(relativePath).normalize() }.getOrNull() ?: return null
        return if (resolved.startsWith(docsDirPath)) {
            resolved
        } else {
            null
        }
    }

    private fun composeVerifiedPreviewRouteEntry(
        markdownRelativePath: String,
        baseUrl: String,
        useDirectoryUrls: Boolean,
    ): VerifiedPreviewRouteEntry? {
        val route = routeMappingService.mapToRoute(
            selectedPath = "docs/${markdownRelativePath.trimStart('/')}",
            useDirectoryUrls = useDirectoryUrls,
        ) ?: return null
        return VerifiedPreviewRouteEntry(
            route = route,
            targetUrl = composePreviewTargetUrl(baseUrl, route),
        )
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
            projectId = project.locationHash,
            selectedPath = "docs/$docsRelativePath",
            useDirectoryUrls = scope.useDirectoryUrls,
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
        val scope = resolveActiveRuntimeScope()
        val docsDirPath = scope.docsDirPath
        if (docsDirPath == null || !runCatching { Files.isDirectory(docsDirPath) }.getOrDefault(false)) {
            withStateLock {
                if (verifiedPreviewRouteCacheState.baseUrl == baseUrl) {
                    verifiedPreviewRouteCacheState = verifiedPreviewRouteCacheState.copy(entries = emptyMap())
                }
            }
            return
        }

        val rebuilt = runCatching {
            val entries = linkedMapOf<String, VerifiedPreviewRouteEntry>()
            Files.walk(docsDirPath, MAX_DOCS_ROUTE_SCAN_DEPTH).use { paths ->
                paths.forEach { candidate ->
                    if (!runCatching { Files.isRegularFile(candidate) }.getOrDefault(false)) {
                        return@forEach
                    }
                    val relativePath = runCatching {
                        docsDirPath.relativize(candidate).toString().replace('\\', '/')
                    }.getOrNull() ?: return@forEach
                    val markdownRelative = normalizeMarkdownRelativePath(relativePath) ?: return@forEach
                    val route = routeMappingService.mapToRoute(
                        selectedPath = "docs/${markdownRelative.trimStart('/')}",
                        useDirectoryUrls = scope.useDirectoryUrls,
                    ) ?: return@forEach
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
        withStateLock {
            if (verifiedPreviewRouteCacheState.baseUrl == baseUrl) {
                verifiedPreviewRouteCacheState = VerifiedPreviewRouteCacheState(
                    baseUrl = baseUrl,
                    entries = rebuilt,
                )
            }
        }
    }

    private fun lookupVerifiedPreviewRouteEntry(selectedPath: String): VerifiedPreviewRouteEntry? {
        ensureVerifiedPreviewRouteCacheBaseUrl() ?: return null
        val key = previewRouteCacheKey(selectedPath) ?: return null
        return withStateLock { verifiedPreviewRouteCacheState.entries[key] }
    }

    private fun cacheVerifiedPreviewRouteEntry(
        selectedPath: String,
        entry: VerifiedPreviewRouteEntry,
    ) {
        val baseUrl = ensureVerifiedPreviewRouteCacheBaseUrl() ?: return
        val key = previewRouteCacheKey(selectedPath) ?: return
        withStateLock {
            val state = verifiedPreviewRouteCacheState
            if (state.baseUrl != baseUrl) {
                return@withStateLock
            }
            val updated = LinkedHashMap(state.entries)
            updated[key] = entry
            verifiedPreviewRouteCacheState = VerifiedPreviewRouteCacheState(
                baseUrl = baseUrl,
                entries = updated,
            )
        }
    }

    private fun ensureVerifiedPreviewRouteCacheBaseUrl(): String? {
        val baseUrl = currentPreviewBaseUrl()?.takeIf { it.isNotBlank() }
        if (baseUrl == null) {
            clearVerifiedPreviewRouteCache()
            return null
        }
        withStateLock {
            if (verifiedPreviewRouteCacheState.baseUrl != baseUrl) {
                verifiedPreviewRouteCacheState = VerifiedPreviewRouteCacheState(
                    baseUrl = baseUrl,
                    entries = emptyMap(),
                )
                recentlyReadyRouteExpiryByUrl.clear()
            }
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
        withStateLock {
            verifiedPreviewRouteCacheState = VerifiedPreviewRouteCacheState()
        }
    }

    private fun rememberReadyRouteForFastPath(url: String) {
        val normalized = url.trim()
        if (normalized.isEmpty()) {
            return
        }
        val now = System.currentTimeMillis()
        val expiresAt = now + TOC_ROUTE_READY_CACHE_TTL_MS
        withStateLock {
            purgeExpiredReadyRoutesLocked(now)
            recentlyReadyRouteExpiryByUrl[normalized] = expiresAt
            while (recentlyReadyRouteExpiryByUrl.size > MAX_READY_ROUTE_CACHE_ENTRIES) {
                val evicted = recentlyReadyRouteExpiryByUrl.keys.firstOrNull() ?: break
                recentlyReadyRouteExpiryByUrl.remove(evicted)
            }
        }
    }

    private fun isKnownReadyRouteForFastPath(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isEmpty()) {
            return false
        }
        val now = System.currentTimeMillis()
        val hit = withStateLock {
            purgeExpiredReadyRoutesLocked(now)
            val expiresAt = recentlyReadyRouteExpiryByUrl[normalized] ?: return@withStateLock false
            expiresAt > now
        }
        return hit
    }

    private fun clearReadyRouteCacheForFastPath() {
        withStateLock {
            recentlyReadyRouteExpiryByUrl.clear()
        }
    }

    private fun purgeExpiredReadyRoutesLocked(now: Long) {
        val iterator = recentlyReadyRouteExpiryByUrl.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
            }
        }
    }

    private fun notifyRouteState(
        listener: ((PreviewRouteFlowState, String) -> Unit)?,
        state: PreviewRouteFlowState,
        url: String,
    ) {
        runCatching { listener?.invoke(state, url) }
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

    private fun currentFingerprintSnapshot(projectPath: String): ConfigFingerprintSnapshot {
        val current = currentConfigFingerprint(projectPath)
        return withStateLock {
            val known = lastConfigFingerprint
            ConfigFingerprintSnapshot(
                hasKnownFingerprint = known != null,
                hasChanged = current != known,
                currentFingerprint = current,
            )
        }
    }

    private fun syncConfigFingerprint(projectPath: String) {
        val snapshot = currentFingerprintSnapshot(projectPath)
        withStateLock {
            lastConfigFingerprint = snapshot.currentFingerprint
        }
    }

    private fun currentConfigFingerprint(projectPath: String): String? {
        val configPath = resolveConfigPath(projectPath) ?: return null
        return createConfigFingerprint(configPath)
    }

    private fun createConfigFingerprint(configPath: Path): String? {
        val normalizedPath = configPath.toAbsolutePath().normalize()
        val content = runCatching { Files.readAllBytes(normalizedPath) }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(normalizedPath.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(content)
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
        val activeContext = siteContextResolver.resolveFromRuntimeCommand(projectRoot, command)
            ?: projectRoot?.let(siteContextResolver::resolveProjectDefault)
        val configPath = activeContext?.configPath
        val docsDirPath = activeContext?.docsDirPath ?: projectRoot?.resolve("docs")?.normalize()
        return RuntimeScope(
            configPath = configPath,
            docsDirPath = docsDirPath,
            useDirectoryUrls = activeContext?.useDirectoryUrls ?: true,
        )
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
        val useDirectoryUrls: Boolean,
    )
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
    @Volatile
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
