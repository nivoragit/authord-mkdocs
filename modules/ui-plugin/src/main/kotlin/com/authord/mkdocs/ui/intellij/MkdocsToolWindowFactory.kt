package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ui.PluginCompositionRoot
import com.authord.mkdocs.runtime.DocsFileGatewayAdapter
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

private fun defaultTopicTreeUiService(project: Project): TopicTreeUiService {
    val instanceRegistry = InstanceRegistryService()
    project.basePath?.takeIf { it.isNotBlank() }?.let { projectRoot ->
        instanceRegistry.discoverDefaultInstance(projectRoot)
    }
    val orchestrator = TopicTreeSyncOrchestratorService(
        topicTreePort = TopicTreeMutationService(),
        mkDocsConfigGateway = MkDocsYamlGateway(),
        docsFileGateway = DocsFileGatewayAdapter(),
    )
    val applicationService = TopicTreeApplicationServiceImpl(orchestrator, instanceRegistry)
    return TopicTreeUiServiceImpl(applicationService, instanceRegistry)
}
 
/**
 * Creates a minimal MkDocs tool window shell for plugin entry-point validation.
 */
class MkdocsToolWindowFactory(
    private val runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val resultPresenter: (Project, String, Boolean) -> Unit = ::presentPreviewResult,
    private val previewContentFactory: () -> PreviewContent = ::createDefaultPreviewContent,
    private val activeEditorPathProvider: (Project) -> String? = { project ->
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
    },
    private val typingListenerRegistrar: (Project, DocumentListener) -> Unit = { project, listener ->
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, project)
        }
    },
    private val visibleAreaListenerRegistrar: (Project, VisibleAreaListener) -> Unit = { project, listener ->
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(listener, project)
        }
    },
    private val startupReconciliationResolver: (Project) -> StartupReconciliationCoordinator = {
        StartupReconciliationCoordinator()
    },
    private val defaultInstanceResolver: (String) -> TopicGatewayResult<TopicInstanceRef?> = { projectRoot ->
        InstanceRegistryService().discoverDefaultInstance(projectRoot)
    },
    private val configLoader: (TopicInstanceRef) -> TopicGatewayResult<MkDocsConfigDocument> = { instance ->
        MkDocsYamlGateway().loadConfig(instance)
    },
    private val startupStateListener: (Project, StartupTreeState) -> Unit = { _, _ -> },
    private val topicTreeUiServiceResolver: (Project) -> TopicTreeUiService = {
        defaultTopicTreeUiService(it)
    },
    private val actionControllerFactory: (TopicTreeUiService, TopicTreeFailureRecoveryPresenter) -> TopicTreeActionController =
        { uiService, recoveryPresenter ->
            TopicTreeActionController(uiService = uiService, failureRecoveryPresenter = recoveryPresenter)
        },
    private val dragDropControllerFactory: (TopicTreeUiService, TopicTreeFailureRecoveryPresenter) -> TopicTreeDragDropController =
        { uiService, recoveryPresenter ->
            TopicTreeDragDropController(uiService = uiService, failureRecoveryPresenter = recoveryPresenter)
        },
    private val instanceSwitchCoordinatorFactory: (TopicTreeUiService) -> InstanceSwitchCoordinator = { uiService ->
        InstanceSwitchCoordinator(uiService = uiService)
    },
    private val delayedInvoker: (delayMillis: Long, task: () -> Unit) -> Unit = { delayMillis, task ->
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@executeOnPooledThread
            }

            ApplicationManager.getApplication().invokeLater(task, ModalityState.any())
        }
    },
) : ToolWindowFactory, DumbAware {
    private val typingRefreshDelaysMs: List<Long> = listOf(450L)
    private val typingGenerationByProject = ConcurrentHashMap<String, AtomicInteger>()
    private val scrollSyncEngineByProject = ConcurrentHashMap<String, MkdocsScrollSyncEngine>()
    private val topicTreeControllersByProject = ConcurrentHashMap<String, TopicTreeControllers>()
    /**
     * Registers minimal content inside the tool window manager.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val runtimeService = runtimeServiceResolver(project)
        val previewContent = previewContentFactory()
        wireTopicTreeControllers(project)
        val topicTreePanel = createTopicTreePanel(project)
        val panel = createShellContentPanel(project, runtimeService, previewContent, topicTreePanel.component)
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(panel, "", false)
        contentManager.removeAllContents(true)
        contentManager.addContent(content)

        val initialResult = runtimeService.startPreview(PreviewStartTrigger.TOOL_WINDOW)
        val initialMessage = formatPreviewResultMessage(initialResult)
        if (initialResult.success) {
            val resolvedUrl = initialResult.previewUrl.ifBlank { runtimeService.currentPreviewUrl().orEmpty() }
            if (resolvedUrl.isNotBlank()) {
                previewContent.loadUrl(resolvedUrl)
            }
        }
        resultPresenter(project, initialMessage, initialResult.success)
        registerEditorSelectionSync(project, runtimeService, previewContent)
        // registerDocumentTypingSync(project, runtimeService, previewContent) todo remove
        registerEditorScrollSync(project, runtimeService, previewContent)
        registerTopicTreeReconciliationTriggers(project, topicTreePanel)

        runStartupReconciliation(project)?.let { startupState ->
            topicTreePanel.render(startupState)
            startupStateListener(project, startupState)
        }
    }

    /**
     * Keeps the tool window available for all open projects.
     */
    override fun shouldBeAvailable(project: Project): Boolean = true

    internal fun createShellContentPanel(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService = runtimeServiceResolver(project),
        previewContent: PreviewContent = previewContentFactory(),
        topicTreeComponent: JComponent? = null,
    ): JPanel {
        val panel = JPanel(BorderLayout())

        panel.add(JLabel("Authord"), BorderLayout.NORTH)
        topicTreeComponent?.let { panel.add(it, BorderLayout.WEST) }
        panel.add(previewContent.component, BorderLayout.CENTER)

        runtimeService.currentPreviewUrl()?.takeIf { it.isNotBlank() }?.let { url ->
            previewContent.loadUrl(url)
        }

        return panel
    }

    private fun registerEditorSelectionSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            /**
             * Listens for active editor selection changes to keep preview routing aligned.
             */
            object : FileEditorManagerListener {
                /**
                 * Routes preview to the newly selected docs markdown file.
                 */
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val selectedPath = event.newFile?.path ?: return
                    applyPreviewRoute(project, runtimeService, previewContent, selectedPath)
                }
            },
        )
    }

    private fun registerDocumentTypingSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        typingListenerRegistrar(
            project,
            /**
             * Observes typing events to schedule delayed preview refreshes.
             */
            object : DocumentListener {
                /**
                 * Schedules refresh after edits in the currently active docs file.
                 */
                override fun documentChanged(event: DocumentEvent) {
                    val virtualFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    scheduleTypingRefresh(
                        project = project,
                        runtimeService = runtimeService,
                        previewContent = previewContent,
                        selectedPath = virtualFile.path,
                        document = event.document,
                    )
                }
            },
        )
    }

    private fun registerEditorScrollSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        val engine = syncEngine(project.locationHash)
        visibleAreaListenerRegistrar(
            project,
            /**
             * Tracks editor viewport movement for percentage-based preview scroll sync.
             */
            object : VisibleAreaListener {
                /**
                 * Applies viewport-derived progress to preview scroll position.
                 */
                override fun visibleAreaChanged(event: VisibleAreaEvent) {
                    if (engine.isEditorEventSuppressed()) {
                        return
                    }

                    val virtualFile = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
                    val selectedPath = virtualFile.path
                    val oldRectangle = event.oldRectangle ?: return
                    val newRectangle = event.newRectangle
                    val rawDelta = newRectangle.y - oldRectangle.y
                    if (rawDelta == 0) {
                        return
                    }

                    val visibleOffsets = resolveVisibleOffsets(event.editor, newRectangle.y, newRectangle.height)
                        ?: return

                    val topLine = event.editor.document.getLineNumber(visibleOffsets.startOffset)
                    scheduleScrollSync(
                        project = project,
                        runtimeService = runtimeService,
                        previewContent = previewContent,
                        selectedPath = selectedPath,
                        topLine = topLine,
                        document = event.editor.document,
                        rawDelta = rawDelta,
                        visibleStartOffset = visibleOffsets.startOffset,
                        visibleEndOffset = visibleOffsets.endOffset,
                    )
                }
            },
        )
    }

    private fun registerTopicTreeReconciliationTriggers(
        project: Project,
        topicTreePanel: TopicTreeWorkspacePanel,
    ) {
        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    topicTreePanel.reconcileFromDisk()
                }
            },
        )
    }



    internal fun applyPreviewRoute(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
    ): Boolean {
        val updatedUrl = runtimeService.navigateToSelectedFile(selectedPath) ?: return false
        previewContent.loadUrl(updatedUrl)
        resultPresenter(project, "MkDocs preview updated: $updatedUrl", true)
        return true
    }

    internal fun refreshCurrentPreviewForDocsSave(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
    ): Boolean {
        if (!runtimeService.isRuntimeRunning()) {
            return false
        }

        if (!isDocsMarkdownPath(selectedPath)) {
            return false
        }

        val currentUrl = runtimeService.currentPreviewUrl()?.takeIf { it.isNotBlank() } ?: return false
        previewContent.loadUrl(currentUrl)
        resultPresenter(project, "MkDocs preview refreshed: $currentUrl", true)
        return true
    }

    internal fun scheduleTypingRefresh(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
        document: Document? = null,
    ): Boolean {
        if (!runtimeService.isRuntimeRunning() || !isDocsMarkdownPath(selectedPath)) {
            return false
        }

        val activePath = activeEditorPathProvider(project) ?: return false
        if (!isSamePath(activePath, selectedPath)) {
            return false
        }

        val generationCounter = typingGenerationByProject.computeIfAbsent(project.locationHash) { AtomicInteger(0) }
        val generation = generationCounter.incrementAndGet()

        typingRefreshDelaysMs.forEach { delay ->
            delayedInvoker(delay) {
                if (project.isDisposed || generationCounter.get() != generation) {
                    return@delayedInvoker
                }

                saveDocumentForTypingRefresh(document)
                val applied = applyPreviewRoute(project, runtimeService, previewContent, selectedPath)
                if (!applied) {
                    refreshCurrentPreviewForDocsSave(project, runtimeService, previewContent, selectedPath)
                }
            }
        }

        return true
    }

    /**
     * Schedules smooth preview scroll from editor-visible area events.
     * Mapping rule: editor viewport progress percentage is applied directly to preview.
     */
    internal fun scheduleScrollSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
        topLine: Int,
        document: Document,
        rawDelta: Int,
        visibleStartOffset: Int,
        visibleEndOffset: Int,
    ): Boolean {
        if (!runtimeService.isRuntimeRunning() || !isDocsMarkdownPath(selectedPath) || rawDelta == 0) {
            return false
        }

        val activePath = activeEditorPathProvider(project) ?: return false
        if (!isSamePath(activePath, selectedPath)) {
            return false
        }

        val engine = syncEngine(project.locationHash)
        val units = engine.resolveEffectiveUnits(document)
        if (units.units.total <= 0) {
            return false
        }

        engine.onEditorScroll(
            topLine = topLine,
            selectedPath = selectedPath,
            lines = units.lines,
            units = units.units,
            applyRatio = { ratio ->
                // Ratio sync uses scrollToProgress
                engine.suppressPreviewEvents()
                previewContent.scrollToProgress(ratio)
            },
            applyPrecise = { y ->
                // Precise sync uses scrollToY
                engine.suppressPreviewEvents()
                previewContent.scrollToY(y)
            },
            loadPreviewSnapshot = { callback ->
                previewContent.requestDomSnapshot(callback)
            },
            loadPreviewMetrics = { callback ->
                previewContent.requestScrollMetrics(callback)
            },
        )

        return true
    }

    private fun resolveEffectiveUnits(projectId: String, document: Document): EffectiveUnitsCache {
        return syncEngine(projectId).resolveEffectiveUnits(document)
    }

    internal fun computeEffectiveUnits(lines: List<String>): EffectiveUnitsResult {
        return MkdocsScrollSyncEngine(delayedInvoker = { _, _ -> }).computeEffectiveUnits(lines)
    }

    private fun isDocsMarkdownPath(selectedPath: String): Boolean {
        val normalized = selectedPath.replace('\\', '/')
        return normalized.endsWith(".md") && normalized.contains("/docs/")
    }

    private fun isSamePath(left: String, right: String): Boolean {
        return left.replace('\\', '/') == right.replace('\\', '/')
    }

    private fun saveDocumentForTypingRefresh(document: Document?) {
        document ?: return
        runCatching {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    private data class VisibleOffsets(
        val startOffset: Int,
        val endOffset: Int,
    )

    private fun resolveVisibleOffsets(editor: Editor, visibleY: Int, visibleHeight: Int): VisibleOffsets? {
        if (visibleHeight <= 0) {
            return null
        }

        val start = editor.logicalPositionToOffset(editor.xyToLogicalPosition(Point(0, visibleY)))
        val endRaw = editor.logicalPositionToOffset(editor.xyToLogicalPosition(Point(0, visibleY + visibleHeight)))
        val end = endRaw.coerceAtLeast(start + 1)
        return VisibleOffsets(start, end)
    }

    private fun scrollEditorToLine(project: Project, line: Int) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val safeLine = line.coerceIn(0, editor.document.lineCount.coerceAtLeast(1) - 1)
        val y = editor.logicalPositionToXY(LogicalPosition(safeLine, 0)).y
        syncEngine(project.locationHash).suppressEditorEvents()
        editor.scrollingModel.scrollVertically(y)
    }

    private fun syncEngine(projectId: String): MkdocsScrollSyncEngine {
        return scrollSyncEngineByProject.computeIfAbsent(projectId) {
            MkdocsScrollSyncEngine(delayedInvoker = delayedInvoker)
        }
    }

    /**
     * Wires US2 topic-tree action and drag/drop controllers into this tool-window lifecycle.
     */
    private fun wireTopicTreeControllers(project: Project) {
        val uiService = topicTreeUiServiceResolver(project)
        val recoveryPresenter = TopicTreeFailureRecoveryPresenter()
        val instanceRegistryPort = (uiService as? TopicTreeUiServiceImpl)?.instanceRegistryPort
        topicTreeControllersByProject[project.locationHash] = TopicTreeControllers(
            actionController = actionControllerFactory(uiService, recoveryPresenter),
            dragDropController = dragDropControllerFactory(uiService, recoveryPresenter),
            failureRecoveryPresenter = recoveryPresenter,
            instanceSwitchCoordinator = instanceSwitchCoordinatorFactory(uiService),
            instanceRegistryPort = instanceRegistryPort,
        )
    }

    /**
     * Exposes controller wiring for tests and lifecycle diagnostics.
     */
    internal fun topicTreeControllers(project: Project): TopicTreeControllers? {
        return topicTreeControllersByProject[project.locationHash]
    }

    /**
     * Applies instance selection interaction from tool-window controls.
     */
    internal fun selectTopicTreeInstance(
        project: Project,
        instanceId: String,
    ): TopicGatewayResult<InstanceSwitchOutcome>? {
        val controllers = topicTreeControllers(project) ?: return null
        return controllers.instanceSwitchCoordinator.switchActiveInstance(instanceId)
    }

    /**
     * Runs startup reconciliation during tool-window initialization using non-destructive defaults.
     */
    internal fun runStartupReconciliation(project: Project): StartupTreeState? {
        val projectRoot = project.basePath?.let { basePath ->
            runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull()
        } ?: return null

        val defaultInstance = when (val result = defaultInstanceResolver(projectRoot.toString())) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> null
        }

        val docsDirPath = defaultInstance
            ?.let { instance -> runCatching { Path.of(instance.docsDirPath).toAbsolutePath().normalize() }.getOrNull() }
            ?: projectRoot.resolve("docs").normalize()

        val configDocument = defaultInstance
            ?.let { instance ->
                when (val result = configLoader(instance)) {
                    is TopicGatewayResult.Success -> result.value.copy(
                        docsDir = docsDirPath.toString().replace('\\', '/'),
                    )
                    is TopicGatewayResult.Failure -> null
                }
            } ?: MkDocsConfigDocument(
                docsDir = docsDirPath.toString().replace('\\', '/'),
                nav = emptyList(),
            )

        val docsMarkdownPaths = collectDocsMarkdownPaths(docsDirPath)

        return runCatching {
            startupReconciliationResolver(project).reconcile(
                config = configDocument,
                docsMarkdownPaths = docsMarkdownPaths,
                projectId = project.locationHash,
                instanceId = defaultInstance?.instanceId ?: "default",
            )
        }.getOrNull()
    }

    private fun collectDocsMarkdownPaths(docsDirPath: Path): List<String> {
        if (!Files.isDirectory(docsDirPath)) {
            return emptyList()
        }

        val markdownPaths = mutableListOf<String>()
        Files.walk(docsDirPath).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".md") }
                .forEach { path ->
                    markdownPaths += path.toAbsolutePath().normalize().toString().replace('\\', '/')
                }
        }
        return markdownPaths.sorted()
    }

    private fun createTopicTreePanel(project: Project): TopicTreeWorkspacePanel {
        return TopicTreeWorkspacePanel(
            controllersProvider = { topicTreeControllers(project) },
            reconcileStateProvider = { runStartupReconciliation(project) },
            startupStateListener = { state -> startupStateListener(project, state) },
            projectRootPath = project.basePath,
        )
    }

}

/**
 * Tool-window scoped bundle for topic-tree action, drag/drop, and failure guidance wiring.
 */
data class TopicTreeControllers(
    val actionController: TopicTreeActionController,
    val dragDropController: TopicTreeDragDropController,
    val failureRecoveryPresenter: TopicTreeFailureRecoveryPresenter,
    val instanceSwitchCoordinator: InstanceSwitchCoordinator,
    val instanceRegistryPort: InstanceRegistryPort?,
)

data class PreviewScrollMetrics(
    val scrollY: Double,
    val maxScrollY: Double,
)

data class PreviewHeadingAnchor(
    val text: String,
    val y: Double,
)

data class PreviewDomSnapshot(
    val maxScrollY: Double,
    val headings: List<PreviewHeadingAnchor>,
)

/**
 * Contract for the preview surface embedded in the MkDocs tool window.
 */
interface PreviewContent {
    /**
     * Swing component rendered inside the tool window content panel.
     */
    val component: JComponent

    /**
     * Navigates preview to the provided URL.
     *
     * @param url absolute preview URL.
     */
    fun loadUrl(url: String)

    /**
     * Applies smooth vertical scroll to already-loaded preview content.
     */
    fun scrollBy(delta: Int) = Unit

    /**
     * Scrolls preview to a normalized vertical progress position.
     *
     * @param progress value within [0.0, 1.0] where 0 is top and 1 is bottom.
     */
    fun scrollToProgress(progress: Double) = Unit

    /**
     * Scrolls preview to an absolute Y coordinate in CSS pixels.
     */
    fun scrollToY(y: Double) = Unit

    /**
     * Requests the current preview scroll metrics.
     */
    fun requestScrollMetrics(callback: (PreviewScrollMetrics?) -> Unit) {
        callback(null)
    }

    /**
     * Requests heading anchor snapshot from the preview DOM.
     */
    fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        callback(null)
    }

    /**
     * Registers preview scroll listener.
     */
    fun setPreviewScrollListener(listener: ((PreviewScrollMetrics) -> Unit)?) = Unit
}

private class JcefPreviewContent : PreviewContent {
    private val browser = JBCefBrowser()
    private val scrollEventQuery = JBCefJSQuery.create(browser)
    private val metricsQuery = JBCefJSQuery.create(browser)
    private val domSnapshotQuery = JBCefJSQuery.create(browser)
    @Volatile private var previewScrollListener: ((PreviewScrollMetrics) -> Unit)? = null
    @Volatile private var pendingMetricsCallback: ((PreviewScrollMetrics?) -> Unit)? = null
    @Volatile private var pendingDomSnapshotCallback: ((PreviewDomSnapshot?) -> Unit)? = null

    override val component: JComponent = browser.component

    init {
        scrollEventQuery.addHandler { payload ->
            val parsed = parseScrollMetricsPayload(payload) ?: return@addHandler null
            val listener = previewScrollListener ?: return@addHandler null
            ApplicationManager.getApplication().invokeLater(
                { listener(parsed) },
                ModalityState.any(),
            )
            null
        }

        metricsQuery.addHandler { payload ->
            val callback = pendingMetricsCallback
            pendingMetricsCallback = null
            callback ?: return@addHandler null
            val parsed = parseScrollMetricsPayload(payload)
            ApplicationManager.getApplication().invokeLater(
                { callback(parsed) },
                ModalityState.any(),
            )
            null
        }

        domSnapshotQuery.addHandler { payload ->
            val callback = pendingDomSnapshotCallback
            pendingDomSnapshotCallback = null
            callback ?: return@addHandler null
            val parsed = parseDomSnapshotPayload(payload)
            ApplicationManager.getApplication().invokeLater(
                { callback(parsed) },
                ModalityState.any(),
            )
            null
        }

        browser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == false) {
                        return
                    }
                    installPreviewScrollListenerIfNeeded()
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Loads preview content in the embedded Chromium browser.
     */
    override fun loadUrl(url: String) {
        browser.loadURL(withRefreshToken(url))
    }

    /**
     * Applies direct pixel delta scrolling in the embedded browser viewport.
     */
    override fun scrollBy(delta: Int) {
        if (delta == 0) {
            return
        }

        val escapedDelta = delta.toString()
        val script = """
            (function() {
                const delta = Number($escapedDelta);
                window.scrollBy({ top: delta, left: 0, behavior: 'smooth' });
            })();
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(script, "about:blank", 0)
    }

    /**
     * Scrolls the embedded browser to a normalized document progress position.
     *
     * @param progress normalized value in range [0.0, 1.0].
     */
    override fun scrollToProgress(progress: Double) {
        val clamped = progress.coerceIn(0.0, 1.0)
        val script = """
            (function() {
                const targetProgress = Number(${clamped});
                const root = document.scrollingElement || document.documentElement || document.body;
                if (!root) return;
                const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
                const target = maxScroll * targetProgress;
                
                const state = window.__authordPreviewSyncState || { raf: 0, target: 0 };
                if (state.raf) {
                    window.cancelAnimationFrame(state.raf);
                    state.raf = 0;
                }
                
                root.scrollTop = target;
                window.__authordPreviewSyncState = state;
            })();
        """.trimIndent()
        executeScript(script)
    }

    /**
     * Scrolls the embedded browser to an absolute document Y position.
     */
    override fun scrollToY(y: Double) {
        val targetY = y.coerceAtLeast(0.0)
        val script = """
            (function() {
                const target = Number(${targetY});
                const root = document.scrollingElement || document.documentElement || document.body;
                if (!root) return;
                const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
                const safeTarget = Math.min(Math.max(target, 0), maxScroll);
                
                const state = window.__authordPreviewSyncState || { raf: 0, target: 0 };
                if (state.raf) {
                    window.cancelAnimationFrame(state.raf);
                    state.raf = 0;
                }

                root.scrollTop = safeTarget;
                window.__authordPreviewSyncState = state;
            })();
        """.trimIndent()
        executeScript(script)
    }

    /**
     * Returns live preview viewport metrics through a JS bridge callback.
     */
    override fun requestScrollMetrics(callback: (PreviewScrollMetrics?) -> Unit) {
        pendingMetricsCallback = callback
        val script = """
            (function() {
                const root = document.scrollingElement || document.documentElement || document.body;
                if (!root) {
                    const payload = "0|0";
                    ${metricsQuery.inject("payload")};
                    return;
                }
                const scrollY = Number(window.scrollY || root.scrollTop || 0);
                const maxScroll = Math.max(
                    0,
                    Number(root.scrollHeight || 0) - Number(window.innerHeight || root.clientHeight || 0)
                );
                const payload = String(scrollY) + "|" + String(maxScroll);
                ${metricsQuery.inject("payload")};
            })();
        """.trimIndent()
        executeScript(script)
    }

    /**
     * Returns a heading-anchor snapshot used by precise piecewise interpolation.
     */
    override fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        pendingDomSnapshotCallback = callback
        val script = """
            (function() {
                const root = document.scrollingElement || document.documentElement || document.body;
                if (!root) {
                    const payload = "0";
                    ${domSnapshotQuery.inject("payload")};
                    return;
                }

                const currentY = Number(window.scrollY || root.scrollTop || 0);
                const maxScroll = Math.max(
                    0,
                    Number(root.scrollHeight || 0) - Number(window.innerHeight || root.clientHeight || 0)
                );
                const headings = [];
                const nodes = document.querySelectorAll("h1,h2,h3,h4,h5,h6");
                for (const node of nodes) {
                    if (!node || !node.getBoundingClientRect) continue;
                    const rect = node.getBoundingClientRect();
                    const style = window.getComputedStyle(node);
                    if (!style) continue;
                    if (style.display === "none" || style.visibility === "hidden") continue;
                    if (rect.width <= 0 || rect.height <= 0) continue;

                    const text = encodeURIComponent(String(node.textContent || "").trim());
                    const y = Math.max(0, currentY + rect.top);
                    headings.push(text + ":" + String(y));
                }

                const payload = [String(maxScroll)].concat(headings).join("|");
                ${domSnapshotQuery.inject("payload")};
            })();
        """.trimIndent()
        executeScript(script)
    }

    /**
     * Subscribes to preview-side scroll events. Events are rAF-throttled in JS.
     */
    override fun setPreviewScrollListener(listener: ((PreviewScrollMetrics) -> Unit)?) {
        previewScrollListener = listener
        installPreviewScrollListenerIfNeeded()
    }

    private fun installPreviewScrollListenerIfNeeded() {
        if (previewScrollListener == null) {
            return
        }

        val script = """
            (function() {
                if (window.__authordPreviewScrollBridgeInstalled) return;
                window.__authordPreviewScrollBridgeInstalled = true;

                const emit = function() {
                    const root = document.scrollingElement || document.documentElement || document.body;
                    if (!root) return;
                    const scrollY = Number(window.scrollY || root.scrollTop || 0);
                    const maxScroll = Math.max(
                        0,
                        Number(root.scrollHeight || 0) - Number(window.innerHeight || root.clientHeight || 0)
                    );
                    const payload = String(scrollY) + "|" + String(maxScroll);
                    ${scrollEventQuery.inject("payload")};
                };

                let rafToken = 0;
                const onScroll = function() {
                    if (rafToken) return;
                    rafToken = window.requestAnimationFrame(function() {
                        rafToken = 0;
                        emit();
                    });
                };

                window.addEventListener("scroll", onScroll, { passive: true });
                window.addEventListener("resize", onScroll, { passive: true });
                emit();
            })();
        """.trimIndent()
        executeScript(script)
    }

    private fun executeScript(script: String) {
        browser.cefBrowser.executeJavaScript(script, "about:blank", 0)
    }

    private fun parseScrollMetricsPayload(payload: String?): PreviewScrollMetrics? {
        if (payload.isNullOrBlank()) {
            return null
        }

        val parts = payload.split('|', limit = 2)
        if (parts.size < 2) {
            return null
        }

        val scrollY = parts[0].toDoubleOrNull() ?: return null
        val maxScrollY = parts[1].toDoubleOrNull() ?: return null
        return PreviewScrollMetrics(
            scrollY = scrollY.coerceAtLeast(0.0),
            maxScrollY = maxScrollY.coerceAtLeast(0.0),
        )
    }

    private fun parseDomSnapshotPayload(payload: String?): PreviewDomSnapshot? {
        if (payload.isNullOrBlank()) {
            return null
        }

        val segments = payload.split('|')
        val maxScrollY = segments.firstOrNull()?.toDoubleOrNull() ?: 0.0
        val headings = segments.drop(1).mapNotNull { item ->
            if (item.isBlank()) {
                return@mapNotNull null
            }
            val separatorIndex = item.lastIndexOf(':')
            if (separatorIndex <= 0 || separatorIndex >= item.length - 1) {
                return@mapNotNull null
            }

            val encodedText = item.substring(0, separatorIndex)
            val y = item.substring(separatorIndex + 1).toDoubleOrNull() ?: return@mapNotNull null
            val decodedText = runCatching {
                URLDecoder.decode(encodedText, StandardCharsets.UTF_8)
            }.getOrDefault(encodedText)
            if (decodedText.isBlank()) {
                return@mapNotNull null
            }

            PreviewHeadingAnchor(
                text = decodedText,
                y = y.coerceAtLeast(0.0),
            )
        }

        return PreviewDomSnapshot(
            maxScrollY = maxScrollY.coerceAtLeast(0.0),
            headings = headings,
        )
    }

    private fun withRefreshToken(url: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "${url}${separator}__authord_preview_ts=${System.currentTimeMillis()}"
    }
}

private class HtmlPreviewContent : PreviewContent {
    private val editorPane = JEditorPane("text/html", "").apply {
        isEditable = false
        text = """
            <html>
              <body style="font-family:sans-serif;padding:12px;">
                <p>MkDocs preview will load here after startup.</p>
              </body>
            </html>
        """.trimIndent()
    }

    override val component: JComponent = JScrollPane(editorPane)

    /**
     * Shows preview URL fallback content when JCEF is unavailable.
     */
    override fun loadUrl(url: String) {
        editorPane.text = """
            <html>
              <body style="font-family:sans-serif;padding:12px;">
                <p>Embedded browser unavailable in this IDE runtime.</p>
                <p>Open preview URL:</p>
                <p><a href="$url">$url</a></p>
              </body>
            </html>
        """.trimIndent()
    }
}

internal fun createDefaultPreviewContent(): PreviewContent {
    return if (JBCefApp.isSupported()) {
        JcefPreviewContent()
    } else {
        HtmlPreviewContent()
    }
}
