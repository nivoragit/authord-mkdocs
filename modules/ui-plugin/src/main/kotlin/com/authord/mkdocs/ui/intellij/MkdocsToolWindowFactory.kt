package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ui.ActivationResult
import com.authord.mkdocs.ui.PluginCompositionRoot
import com.authord.mkdocs.runtime.DocsFileGatewayAdapter
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.io.path.name
import kotlin.math.abs
import kotlin.math.roundToInt

private val LOG = Logger.getInstance(MkdocsToolWindowFactory::class.java)

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

internal enum class ShellLayoutMode(
    val label: String,
    val description: String,
) {
    PREVIEW(
        label = "Preview",
        description = "Show only the preview panel",
    ),
    PREVIEW_AND_TREEVIEW(
        label = "Preview and Treeview",
        description = "Show preview and topic tree panels",
    ),
    TREEVIEW(
        label = "Treeview",
        description = "Show only the topic tree panel",
    ),
}

/**
 * Creates a minimal Authord tool window shell for plugin entry-point validation.
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
    private val typingListenerRegistrar: (Project, DocumentListener, Disposable) -> Unit = { _, listener, parentDisposable ->
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parentDisposable)
        }
    },
    private val visibleAreaListenerRegistrar: (Project, VisibleAreaListener, Disposable) -> Unit = { _, listener, parentDisposable ->
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(listener, parentDisposable)
        }
    },
    private val vfsBulkListenerRegistrar: (Project, BulkFileListener, Disposable) -> Unit = { project, listener, parentDisposable ->
        project.messageBus.connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, listener)
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
    private val mkDocsProjectCreatorResolver: (Project) -> MkDocsProjectCreator = { MkDocsProjectCreator() },
    private val mkdocsConfigPresenceResolver: (Project) -> Boolean = ::hasMkdocsConfigInProjectRoot,
    private val mkdocsConfigCacheInvalidator: (Project) -> Unit = ::invalidateMkdocsConfigCacheForProject,
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

            ApplicationManager.getApplication().invokeLater(task, ModalityState.defaultModalityState())
        }
    },
    private val markdownPreviewRefresher: (Project, PreviewStartTrigger) -> Boolean = ::refreshOpenAuthordMarkdownPreviews,
    private val previewBrowserServiceResolver: (Project) -> MkDocsPreviewBrowserService? = { project ->
        runCatching { project.getService(MkDocsPreviewBrowserService::class.java) }.getOrNull()
    },
) : ToolWindowFactory, DumbAware {
    private val expandedTreeProportionByProject = ConcurrentHashMap<String, Float>()
    private val shellLayoutModeByProject = ConcurrentHashMap<String, ShellLayoutMode>()
    private val scrollSyncEngineByProject = ConcurrentHashMap<String, MkdocsScrollSyncEngine>()
    private val typingRefreshDelaysMs: List<Long> = listOf(2000L)
    private val typingGenerationByProject = ConcurrentHashMap<String, AtomicInteger>()
    private val lastTypingTimestampByProject = ConcurrentHashMap<String, Long>()
    private val typingScrollGuardMs: Long = 800L
    private val userScrollLatchByProject = ConcurrentHashMap<String, Boolean>()
    private val lastSyncedEditorTopByProject = ConcurrentHashMap<String, Double>()
    private val topicTreePanelsByProject = ConcurrentHashMap<String, TopicTreeWorkspacePanel>()
    private val topicTreeControllersByProject = ConcurrentHashMap<String, TopicTreeControllers>()
    private val topicTreeUiServicesByProject = ConcurrentHashMap<String, TopicTreeUiService>()
    private val previewSyncLifecycleByProject = ConcurrentHashMap<String, Disposable>()
    private val modeWatcherLifecycleByProject = ConcurrentHashMap<String, Disposable>()
    private val previewModeByProject = ConcurrentHashMap<String, Boolean>()
    /**
     * Registers minimal content inside the tool window manager.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        enforceAuthordToolWindowHost(toolWindow)
        if (toolWindow.id == AUTHORD_TREEVIEW_TOOL_WINDOW_ID) {
            createTreeviewToolWindowContent(project, toolWindow)
            return
        }

        val runtimeService = runtimeServiceResolver(project)
        val browserService = previewBrowserServiceResolver(project)
        val previewOwnerId = toolWindowPreviewOwnerId(toolWindow, project)
        val previewContent = browserService?.previewContentForOwner(
            ownerKind = PreviewOwnerKind.TOOL_WINDOW,
            ownerId = previewOwnerId,
        ) ?: resolvePreviewContent(project)
        browserService?.activatePreviewOwner(
            ownerKind = PreviewOwnerKind.TOOL_WINDOW,
            ownerId = previewOwnerId,
        )
        val projectCreator = mkDocsProjectCreatorResolver(project)
        wireTopicTreeControllers(project)
        val topicTreePanel = createTopicTreePanel(project)
        topicTreePanelsByProject[project.locationHash] = topicTreePanel
        val shellContent = createShellContent(project, runtimeService, previewContent, topicTreePanel.component)
        val panel = shellContent.panel
        val splitter = shellContent.splitter
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(panel, resolveAuthordWindowTitle(project), false)
        val projectKey = projectKey(project)
        content.setDisposer(Disposable {
            browserService?.releasePreviewOwner(
                ownerKind = PreviewOwnerKind.TOOL_WINDOW,
                ownerId = previewOwnerId,
            )
            disposePreviewSyncLifecycle(project, previewContent)
            disposeModeWatcher(project)
            mkdocsConfigCacheInvalidator(project)
            previewModeByProject.remove(project.locationHash)
            expandedTreeProportionByProject.remove(projectKey)
            shellLayoutModeByProject.remove(projectKey)
            typingGenerationByProject.remove(project.locationHash)
            lastTypingTimestampByProject.remove(project.locationHash)
            userScrollLatchByProject.remove(project.locationHash)
            lastSyncedEditorTopByProject.remove(project.locationHash)
            topicTreePanelsByProject.remove(project.locationHash)
            topicTreeControllersByProject.remove(project.locationHash)
            topicTreeUiServicesByProject.remove(project.locationHash)
        })
        contentManager.removeAllContents(true)
        contentManager.addContent(content)

        registerTopicTreeReconciliationTriggers(project, topicTreePanel)
        registerModeWatcher(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            topicTreePanel = topicTreePanel,
            splitter = splitter,
            projectCreator = projectCreator,
        )

        if (mkdocsConfigPresenceResolver(project)) {
            enterPreviewMode(
                project = project,
                runtimeService = runtimeService,
                previewContent = previewContent,
                topicTreePanel = topicTreePanel,
                splitter = splitter,
                projectCreator = projectCreator,
                trigger = PreviewStartTrigger.TOOL_WINDOW,
            )
        } else {
            enterSetupMode(
                project = project,
                runtimeService = runtimeService,
                previewContent = previewContent,
                topicTreePanel = topicTreePanel,
                splitter = splitter,
                projectCreator = projectCreator,
            )
        }
    }

    private fun toolWindowPreviewOwnerId(toolWindow: ToolWindow, project: Project): String {
        return "${toolWindow.id}:${project.locationHash}"
    }

    private fun enforceAuthordToolWindowHost(toolWindow: ToolWindow) {
        runCatching {
            toolWindow.setAnchor(ToolWindowAnchor.LEFT, null)
            toolWindow.setType(ToolWindowType.DOCKED, null)
            toolWindow.isAutoHide = false
        }
    }

    private fun createTreeviewToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val runtimeService = runtimeServiceResolver(project)
        val projectCreator = mkDocsProjectCreatorResolver(project)
        wireTopicTreeControllers(project)
        val topicTreePanel = createTopicTreePanel(project)
        topicTreePanelsByProject[project.locationHash] = topicTreePanel

        val panel = JPanel(BorderLayout())
        val contentManager = toolWindow.contentManager
        val content = contentManager.factory.createContent(panel, resolveAuthordWindowTitle(project), false)
        content.setDisposer(Disposable {
            disposeModeWatcher(project)
            mkdocsConfigCacheInvalidator(project)
            previewModeByProject.remove(project.locationHash)
        })
        contentManager.removeAllContents(true)
        contentManager.addContent(content)

        registerTopicTreeReconciliationTriggers(project, topicTreePanel)
        registerTreeviewModeWatcher(project, panel, topicTreePanel, runtimeService, projectCreator)

        if (mkdocsConfigPresenceResolver(project)) {
            enterTreeviewMode(project, panel, topicTreePanel)
            startRuntimeForConfirmedConfig(project, runtimeService, PreviewStartTrigger.TOOL_WINDOW)
        } else {
            enterTreeviewSetupMode(project, panel, topicTreePanel, runtimeService, projectCreator)
        }
    }

    private fun enterTreeviewMode(
        project: Project,
        panel: JPanel,
        topicTreePanel: TopicTreeWorkspacePanel,
    ) {
        updateAuthordWindowTitle(project, resolveAuthordWindowTitle(project))
        previewModeByProject[project.locationHash] = true
        panel.removeAll()
        panel.add(topicTreePanel.component, BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
        topicTreePanel.reconcileFromDisk()
        runStartupReconciliation(project)?.let { startupState ->
            topicTreePanel.render(startupState)
            startupStateListener(project, startupState)
        }
    }

    private fun enterTreeviewSetupMode(
        project: Project,
        panel: JPanel,
        topicTreePanel: TopicTreeWorkspacePanel,
        runtimeService: PluginRuntimeIntegrationService,
        projectCreator: MkDocsProjectCreator,
    ) {
        previewModeByProject[project.locationHash] = false
        panel.removeAll()
        panel.add(
            SetupPanel(
                onProjectCreate = { requestedName ->
                    handleTreeviewSetupProjectCreate(
                        project = project,
                        requestedName = requestedName,
                        panel = panel,
                        topicTreePanel = topicTreePanel,
                        runtimeService = runtimeService,
                        projectCreator = projectCreator,
                    )
                },
                suggestedProjectName = resolveDefaultSetupProjectName(project),
            ),
            BorderLayout.CENTER,
        )
        panel.revalidate()
        panel.repaint()
        LOG.info(
            "Entering setup mode for project `${project.name}` in Authord Treeview: configuration file not found in project root.",
        )
    }

    private fun handleTreeviewSetupProjectCreate(
        project: Project,
        requestedName: String,
        panel: JPanel,
        topicTreePanel: TopicTreeWorkspacePanel,
        runtimeService: PluginRuntimeIntegrationService,
        projectCreator: MkDocsProjectCreator,
    ) {
        val projectPath = project.basePath
        if (projectPath.isNullOrBlank()) {
            resultPresenter(project, "Project path is unavailable.", false)
            return
        }

        runInBackground {
            val creationResult = projectCreator.createProject(projectPath, requestedName)
            runOnUiThread {
                if (project.isDisposed) {
                    return@runOnUiThread
                }
                if (!creationResult.success) {
                    resultPresenter(
                        project,
                        creationResult.message.ifBlank { "Failed to create project." },
                        false,
                    )
                    return@runOnUiThread
                }

                refreshProjectRoot(projectPath)
                mkdocsConfigCacheInvalidator(project)
                openDefaultIndexInEditor(project, projectPath)
                updateAuthordWindowTitle(project, requestedName)
                enterTreeviewMode(project, panel, topicTreePanel)
                startRuntimeForConfirmedConfig(project, runtimeService, PreviewStartTrigger.TOOL_WINDOW)
            }
        }
    }

    private fun registerTreeviewModeWatcher(
        project: Project,
        panel: JPanel,
        topicTreePanel: TopicTreeWorkspacePanel,
        runtimeService: PluginRuntimeIntegrationService,
        projectCreator: MkDocsProjectCreator,
    ) {
        val projectId = project.locationHash
        modeWatcherLifecycleByProject.remove(projectId)?.let(Disposer::dispose)
        val lifecycle = Disposer.newDisposable("authord.treeviewModeWatcher.$projectId")
        modeWatcherLifecycleByProject[projectId] = lifecycle
        Disposer.register(project, lifecycle)
        vfsBulkListenerRegistrar(
            project,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (project.isDisposed || events.isEmpty()) {
                        return
                    }
                    if (!containsMkdocsConfigRootEvent(project, events)) {
                        return
                    }
                    mkdocsConfigCacheInvalidator(project)
                    runOnUiThread {
                        if (project.isDisposed) {
                            return@runOnUiThread
                        }
                        val hasConfig = mkdocsConfigPresenceResolver(project)
                        val inTreeMode = previewModeByProject[project.locationHash] == true
                        when {
                            hasConfig -> {
                                if (!inTreeMode) {
                                    enterTreeviewMode(
                                        project = project,
                                        panel = panel,
                                        topicTreePanel = topicTreePanel,
                                    )
                                }
                                startRuntimeForConfirmedConfig(
                                    project = project,
                                    runtimeService = runtimeService,
                                    trigger = PreviewStartTrigger.TOOL_WINDOW,
                                )
                            }

                            inTreeMode -> enterTreeviewSetupMode(
                                project = project,
                                panel = panel,
                                topicTreePanel = topicTreePanel,
                                runtimeService = runtimeService,
                                projectCreator = projectCreator,
                            )
                        }
                    }
                }
            },
            lifecycle,
        )
    }

    private fun startRuntimeForConfirmedConfig(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        trigger: PreviewStartTrigger,
    ) {
        if (runtimeService.isRuntimeRunning()) {
            return
        }
        runtimeService.startPreviewWithProgress(trigger) { initialResult ->
            if (project.isDisposed) {
                return@startPreviewWithProgress
            }
            resultPresenter(project, formatPreviewResultMessage(initialResult), initialResult.success)
            if (initialResult.success) {
                markdownPreviewRefresher(project, trigger)
            }
        }
    }

    private fun resolvePreviewContent(project: Project): PreviewContent {
        val browserService = previewBrowserServiceResolver(project)
        return browserService?.ensurePreviewContent() ?: previewContentFactory()
    }

    private fun shouldAutoStartPreviewRuntime(project: Project): Boolean {
        val browserService = previewBrowserServiceResolver(project) ?: return true
        return browserService.markdownPreviewActivated()
    }

    private fun shouldLoadLivePreview(project: Project): Boolean {
        val browserService = previewBrowserServiceResolver(project) ?: return true
        return browserService.markdownPreviewActivated()
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
    ): JComponent {
        return createShellContent(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            topicTreeComponent = topicTreeComponent,
        ).panel
    }

    private fun createShellContent(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        topicTreeComponent: JComponent?,
    ): ShellContent {
        val splitter = if (topicTreeComponent != null) {
            OnePixelSplitter(false, 0.7f).apply {
                firstComponent = previewContent.component
                secondComponent = topicTreeComponent
                setResizeEnabled(true)
            }
        } else {
            null
        }
        val projectKey = projectKey(project)
        splitter?.let { shellSplitter ->
            val initialMode = shellLayoutModeByProject[projectKey] ?: ShellLayoutMode.PREVIEW_AND_TREEVIEW
            setShellLayoutMode(projectKey, shellSplitter, initialMode)
        }
        val content = splitter ?: previewContent.component

        val panel = JPanel(BorderLayout())
        panel.add(
            createShellToolbar(
                target = panel,
                project = project,
                runtimeService = runtimeService,
                previewContent = previewContent,
                splitter = splitter,
            ),
            BorderLayout.NORTH,
        )
        panel.add(content, BorderLayout.CENTER)

        runtimeService.currentPreviewUrl()?.takeIf { it.isNotBlank() }?.let { url ->
            if (mkdocsConfigPresenceResolver(project) && shouldLoadLivePreview(project)) {
                previewContent.loadUrl(url)
            }
        }

        return ShellContent(
            panel = panel,
            splitter = splitter,
        )
    }

    internal fun createShellToolbar(
        target: JComponent,
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        splitter: OnePixelSplitter?,
    ): JComponent {
        if (ApplicationManager.getApplication() == null) {
            return JPanel(BorderLayout()).apply {
                name = "authord-shell-toolbar"
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(createRestartPluginAction(project, runtimeService, previewContent))
            add(createSetShellLayoutModeAction(project, splitter, ShellLayoutMode.PREVIEW))
            add(createSetShellLayoutModeAction(project, splitter, ShellLayoutMode.PREVIEW_AND_TREEVIEW))
            add(createSetShellLayoutModeAction(project, splitter, ShellLayoutMode.TREEVIEW))
        }
        val toolbarComponent = runCatching {
            val toolbar = ActionManager.getInstance().createActionToolbar("AuthordShellToolbar", actionGroup, true)
            toolbar.targetComponent = target
            toolbar.component
        }.getOrElse {
            JPanel(BorderLayout())
        }
        
        val alignRightPanel = JPanel(BorderLayout())
        alignRightPanel.add(toolbarComponent, BorderLayout.EAST)
        alignRightPanel.name = "authord-shell-toolbar"
        return alignRightPanel
    }

    internal fun createRestartPluginAction(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ): AnAction {
        return object : com.intellij.openapi.project.DumbAwareAction(
            "Restart plugin",
            "Restart the Authord preview plugin runtime",
            AllIcons.Actions.Refresh,
        ) {
            override fun actionPerformed(event: AnActionEvent) {
                restartPluginAsync(project, runtimeService, previewContent)
            }
        }
    }

    internal fun restartPlugin(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ): ActivationResult {
        runCatching {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        val result = runtimeService.restartPreview(PreviewStartTrigger.TOOL_WINDOW)
        return applyRestartResult(project, runtimeService, previewContent, result)
    }

    private fun refreshTopicTreeAfterRestart(project: Project) {
        val topicTreePanel = topicTreePanelsByProject[project.locationHash] ?: return
        topicTreePanel.reconcileFromDisk()
        runStartupReconciliation(project)?.let { startupState ->
            topicTreePanel.render(startupState)
            startupStateListener(project, startupState)
        }
    }

    internal fun createSetShellLayoutModeAction(
        project: Project,
        splitter: OnePixelSplitter?,
        mode: ShellLayoutMode,
    ): AnAction {
        val projectKey = projectKey(project)
        return object : com.intellij.openapi.project.DumbAwareAction(
            mode.label,
            mode.description,
            null,
        ) {
            override fun getActionUpdateThread(): com.intellij.openapi.actionSystem.ActionUpdateThread =
                com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

            override fun actionPerformed(event: AnActionEvent) {
                if (!isPreviewModeActive(project) && mode != ShellLayoutMode.PREVIEW) {
                    return
                }
                val shellSplitter = splitter ?: return
                setShellLayoutMode(projectKey, shellSplitter, mode)
            }

            override fun update(event: AnActionEvent) {
                val treeComponent = splitter?.secondComponent
                val hasTree = treeComponent != null
                if (!hasTree) {
                    event.presentation.isVisible = false
                    event.presentation.isEnabled = false
                    return
                }

                val inPreviewMode = isPreviewModeActive(project)
                val modeVisible = inPreviewMode || mode == ShellLayoutMode.PREVIEW
                event.presentation.isVisible = modeVisible
                if (!modeVisible) {
                    event.presentation.isEnabled = false
                    return
                }
                if (!inPreviewMode && mode == ShellLayoutMode.PREVIEW) {
                    event.presentation.isEnabled = false
                    return
                }

                val shellSplitter = splitter ?: return
                event.presentation.isEnabled = resolveShellLayoutMode(projectKey, shellSplitter) != mode
            }
        }
    }

    private fun projectKey(project: Project): String {
        return runCatching { project.locationHash }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: project.name
    }

    private fun resolveDefaultSetupProjectName(project: Project): String {
        val projectName = runCatching { project.name }.getOrNull().orEmpty().trim()
        if (projectName.isNotBlank()) {
            return projectName
        }
        val basePathName = project.basePath
            ?.let { basePath ->
                runCatching {
                    Path.of(basePath).fileName?.toString().orEmpty().trim()
                }.getOrDefault("")
            }
            .orEmpty()
        return basePathName.ifBlank { AuthordUiBundle.message("activation.default.siteName") }
    }

    private fun resolveAuthordWindowTitle(project: Project): String {
        return resolveConfiguredSiteName(project) ?: resolveDefaultSetupProjectName(project)
    }

    private fun resolveConfiguredSiteName(project: Project): String? {
        val rootPath = project.basePath
            ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
            ?: return null
        val configPath = findMkdocsConfig(rootPath) ?: return null
        val siteNameRegex = Regex("""^\s*site_name\s*:\s*(.+?)\s*$""")
        val lines = runCatching { Files.readAllLines(configPath) }.getOrNull() ?: return null
        val rawValue = lines
            .asSequence()
            .map { line -> line.substringBefore('#') }
            .mapNotNull { line -> siteNameRegex.find(line)?.groupValues?.get(1) }
            .map { value -> value.trim() }
            .firstOrNull()
            ?: return null
        return rawValue
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .trim()
            .ifBlank { null }
    }

    private fun updateAuthordWindowTitle(project: Project, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isBlank()) {
            return
        }
        if (ApplicationManager.getApplication() == null) {
            return
        }
        val toolWindowManager = runCatching { ToolWindowManager.getInstance(project) }.getOrNull() ?: return
        listOf(AUTHORD_TREEVIEW_TOOL_WINDOW_ID, AUTHORD_LEGACY_TOOL_WINDOW_ID).forEach { toolWindowId ->
            val toolWindow = toolWindowManager.getToolWindow(toolWindowId) ?: return@forEach
            toolWindow.contentManager.contents.forEach { content ->
                runCatching { content.displayName = normalizedTitle }
            }
        }
    }

    private fun resolveShellLayoutMode(projectKey: String, splitter: OnePixelSplitter): ShellLayoutMode {
        shellLayoutModeByProject[projectKey]?.let { return it }
        val previewVisible = splitter.firstComponent?.isVisible != false
        val treeVisible = splitter.secondComponent?.isVisible != false
        val inferred = when {
            previewVisible && treeVisible -> ShellLayoutMode.PREVIEW_AND_TREEVIEW
            previewVisible -> ShellLayoutMode.PREVIEW
            treeVisible -> ShellLayoutMode.TREEVIEW
            else -> ShellLayoutMode.PREVIEW_AND_TREEVIEW
        }
        shellLayoutModeByProject[projectKey] = inferred
        return inferred
    }

    internal fun setShellLayoutMode(projectKey: String, splitter: OnePixelSplitter, mode: ShellLayoutMode) {
        val previewComponent = splitter.firstComponent ?: return
        val treeComponent = splitter.secondComponent ?: return
        if (mode != ShellLayoutMode.PREVIEW_AND_TREEVIEW && previewComponent.isVisible && treeComponent.isVisible) {
            expandedTreeProportionByProject[projectKey] = splitter.proportion.coerceIn(0.05f, 0.95f)
        }
        when (mode) {
            ShellLayoutMode.PREVIEW -> {
                previewComponent.isVisible = true
                treeComponent.isVisible = false
                splitter.proportion = 1.0f
            }

            ShellLayoutMode.PREVIEW_AND_TREEVIEW -> {
                previewComponent.isVisible = true
                treeComponent.isVisible = true
                val restoredProportion = expandedTreeProportionByProject[projectKey] ?: 0.7f
                splitter.proportion = restoredProportion.coerceIn(0.05f, 0.95f)
            }

            ShellLayoutMode.TREEVIEW -> {
                previewComponent.isVisible = false
                treeComponent.isVisible = true
                splitter.proportion = 0.0f
            }
        }
        shellLayoutModeByProject[projectKey] = mode
        splitter.revalidate()
        splitter.repaint()
    }

    private fun enterSetupMode(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        topicTreePanel: TopicTreeWorkspacePanel,
        splitter: OnePixelSplitter?,
        projectCreator: MkDocsProjectCreator,
    ) {
        previewModeByProject[project.locationHash] = false
        runtimeService.stopPreview()
        disposePreviewSyncLifecycle(project, previewContent)
        renderSetupSurface(
            project = project,
            previewContent = previewContent,
            splitter = splitter,
            onProjectCreate = { requestedName ->
                handleSetupProjectCreate(
                    project = project,
                    requestedName = requestedName,
                    runtimeService = runtimeService,
                    previewContent = previewContent,
                    topicTreePanel = topicTreePanel,
                    splitter = splitter,
                    projectCreator = projectCreator,
                )
            },
        )
        LOG.info(
            "Entering setup mode for project `${project.name}`: configuration file not found in project root.",
        )
    }

    private fun enterPreviewMode(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        topicTreePanel: TopicTreeWorkspacePanel,
        splitter: OnePixelSplitter?,
        projectCreator: MkDocsProjectCreator,
        trigger: PreviewStartTrigger,
    ) {
        if (!mkdocsConfigPresenceResolver(project)) {
            enterSetupMode(
                project = project,
                runtimeService = runtimeService,
                previewContent = previewContent,
                topicTreePanel = topicTreePanel,
                splitter = splitter,
                projectCreator = projectCreator,
            )
            return
        }

        splitter?.let {
            if (it.firstComponent !== previewContent.component) {
                it.firstComponent = previewContent.component
            }
            setShellLayoutMode(projectKey(project), it, ShellLayoutMode.PREVIEW_AND_TREEVIEW)
        }
        updateAuthordWindowTitle(project, resolveAuthordWindowTitle(project))
        previewModeByProject[project.locationHash] = true
        if (previewContent.supportsPreviewEditorSync()) {
            registerPreviewSyncLifecycle(project, runtimeService, previewContent)
        } else {
            disposePreviewSyncLifecycle(project, previewContent)
        }

        topicTreePanel.reconcileFromDisk()
        runStartupReconciliation(project)?.let { startupState ->
            topicTreePanel.render(startupState)
            startupStateListener(project, startupState)
        }

        if (!shouldAutoStartPreviewRuntime(project)) {
            if (runtimeService.isRuntimeRunning() && shouldLoadLivePreview(project)) {
                val existingUrl = runtimeService.currentPreviewUrl().orEmpty()
                if (existingUrl.isNotBlank()) {
                    previewContent.loadUrl(existingUrl)
                }
            }
            return
        }

        runtimeService.startPreviewWithProgress(trigger) { initialResult ->
            if (project.isDisposed) {
                return@startPreviewWithProgress
            }
            val initialMessage = formatPreviewResultMessage(initialResult)
            if (initialResult.success) {
                val resolvedUrl = initialResult.previewUrl.ifBlank { runtimeService.currentPreviewUrl().orEmpty() }
                if (resolvedUrl.isNotBlank() && shouldLoadLivePreview(project)) {
                    previewContent.loadUrl(resolvedUrl)
                }
                markdownPreviewRefresher(project, trigger)
            }
            resultPresenter(project, initialMessage, initialResult.success)
        }
    }

    private fun restartPluginAsync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        runCatching {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
        runtimeService.restartPreviewWithProgress(PreviewStartTrigger.TOOL_WINDOW) { result ->
            if (project.isDisposed) {
                return@restartPreviewWithProgress
            }
            applyRestartResult(project, runtimeService, previewContent, result)
        }
    }

    private fun applyRestartResult(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        result: ActivationResult,
    ): ActivationResult {
        val resolvedUrl = result.previewUrl.ifBlank { runtimeService.currentPreviewUrl().orEmpty() }
        if (result.success) {
            refreshTopicTreeAfterRestart(project)
        }
        if (result.success && resolvedUrl.isNotBlank() && shouldLoadLivePreview(project)) {
            syncEngine(project.locationHash).resetSyncState()
            previewContent.loadUrl(resolvedUrl)
        }
        val message = if (result.success) {
            "Authord preview restarted: ${resolvedUrl.ifBlank { "<unknown-url>" }}"
        } else {
            result.message.ifBlank { "Preview restart failed." }
        }
        resultPresenter(project, message, result.success)
        return result.copy(previewUrl = resolvedUrl)
    }

    private fun renderSetupSurface(
        project: Project,
        previewContent: PreviewContent,
        splitter: OnePixelSplitter?,
        onProjectCreate: (String) -> Unit,
    ) {
        splitter?.let { setShellLayoutMode(projectKey(project), it, ShellLayoutMode.PREVIEW) }
        if (splitter != null && previewContent.prefersSetupPanel()) {
            splitter.firstComponent = SetupPanel(
                onProjectCreate = onProjectCreate,
                suggestedProjectName = resolveDefaultSetupProjectName(project),
            )
            return
        }
        if (splitter != null) {
            splitter.firstComponent = previewContent.component
        }
        previewContent.loadSetupPage(onProjectCreate)
    }

    private fun registerPreviewSyncLifecycle(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        disposePreviewSyncLifecycle(project, previewContent)
        val lifecycle = Disposer.newDisposable("authord.previewSync.${project.locationHash}")
        previewSyncLifecycleByProject[project.locationHash] = lifecycle
        Disposer.register(project, lifecycle)
        registerPreviewAnchorInvalidation(project, runtimeService, previewContent, lifecycle)
        registerEditorSelectionSync(project, runtimeService, previewContent, lifecycle)
        registerDocumentTypingSync(project, runtimeService, previewContent, lifecycle)
        registerEditorScrollSync(project, runtimeService, previewContent, lifecycle)
        registerManualScrollSync(project, previewContent, lifecycle)
    }

    private fun disposePreviewSyncLifecycle(project: Project, previewContent: PreviewContent) {
        previewSyncLifecycleByProject.remove(project.locationHash)?.let(Disposer::dispose)
        previewContent.setContentReloadListener(null)
        previewContent.setManualScrollListener(null)
    }

    private fun registerModeWatcher(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        topicTreePanel: TopicTreeWorkspacePanel,
        splitter: OnePixelSplitter?,
        projectCreator: MkDocsProjectCreator,
    ) {
        val projectId = project.locationHash
        modeWatcherLifecycleByProject.remove(projectId)?.let(Disposer::dispose)
        val lifecycle = Disposer.newDisposable("authord.modeWatcher.$projectId")
        modeWatcherLifecycleByProject[projectId] = lifecycle
        Disposer.register(project, lifecycle)
        vfsBulkListenerRegistrar(
            project,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (project.isDisposed || events.isEmpty()) {
                        return
                    }
                    if (!containsMkdocsConfigRootEvent(project, events)) {
                        return
                    }
                    mkdocsConfigCacheInvalidator(project)
                    runOnUiThread {
                        if (project.isDisposed) {
                            return@runOnUiThread
                        }
                        val hasConfig = mkdocsConfigPresenceResolver(project)
                        val inPreviewMode = previewModeByProject[project.locationHash] == true
                        when {
                            hasConfig && !inPreviewMode -> enterPreviewMode(
                                project = project,
                                runtimeService = runtimeService,
                                previewContent = previewContent,
                                topicTreePanel = topicTreePanel,
                                splitter = splitter,
                                projectCreator = projectCreator,
                                trigger = PreviewStartTrigger.TOOL_WINDOW,
                            )

                            !hasConfig && inPreviewMode -> enterSetupMode(
                                project = project,
                                runtimeService = runtimeService,
                                previewContent = previewContent,
                                topicTreePanel = topicTreePanel,
                                splitter = splitter,
                                projectCreator = projectCreator,
                            )
                        }
                    }
                }
            },
            lifecycle,
        )
    }

    private fun disposeModeWatcher(project: Project) {
        modeWatcherLifecycleByProject.remove(project.locationHash)?.let(Disposer::dispose)
    }

    private fun containsMkdocsConfigRootEvent(project: Project, events: List<VFileEvent>): Boolean {
        val projectRoot = normalizedProjectRootPath(project) ?: return false
        return events.any { event ->
            eventPathsForConfigDetection(event).any { rawPath ->
                val candidatePath = runCatching { Path.of(rawPath).toAbsolutePath().normalize() }.getOrNull()
                candidatePath != null &&
                    candidatePath.startsWith(projectRoot) &&
                    isMkdocsConfigPath(candidatePath.toString())
            }
        }
    }

    private fun normalizedProjectRootPath(project: Project): Path? {
        val basePath = project.basePath ?: return null
        return runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull()
    }

    private fun eventPathsForConfigDetection(event: VFileEvent): Set<String> {
        val paths = linkedSetOf(event.path.replace('\\', '/'))
        when (event) {
            is VFileMoveEvent -> {
                paths += "${event.oldParent.path}/${event.file.name}".replace('\\', '/')
            }

            is VFilePropertyChangeEvent -> {
                if (event.propertyName == VirtualFile.PROP_NAME) {
                    val oldName = event.oldValue as? String
                    val newName = event.newValue as? String
                    if (oldName != null && newName != null) {
                        val newPath = event.path
                        val oldPath = if (newPath.endsWith("/$newName")) {
                            newPath.removeSuffix("/$newName") + "/$oldName"
                        } else {
                            newPath
                        }
                        paths += oldPath.replace('\\', '/')
                    }
                }
            }
        }
        return paths
    }

    private fun isPreviewModeActive(project: Project): Boolean {
        return previewModeByProject[project.locationHash] ?: true
    }

    private fun handleSetupProjectCreate(
        project: Project,
        requestedName: String,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        topicTreePanel: TopicTreeWorkspacePanel,
        splitter: OnePixelSplitter?,
        projectCreator: MkDocsProjectCreator,
    ) {
        val projectPath = project.basePath
        if (projectPath.isNullOrBlank()) {
            resultPresenter(project, "Project path is unavailable.", false)
            return
        }

        runInBackground {
            val creationResult = projectCreator.createProject(projectPath, requestedName)
            runOnUiThread {
                if (project.isDisposed) {
                    return@runOnUiThread
                }
                if (!creationResult.success) {
                    resultPresenter(
                        project,
                        creationResult.message.ifBlank { "Failed to create project." },
                        false,
                    )
                    return@runOnUiThread
                }

                refreshProjectRoot(projectPath)
                mkdocsConfigCacheInvalidator(project)
                openDefaultIndexInEditor(project, projectPath)
                updateAuthordWindowTitle(project, requestedName)
                enterPreviewMode(
                    project = project,
                    runtimeService = runtimeService,
                    previewContent = previewContent,
                    topicTreePanel = topicTreePanel,
                    splitter = splitter,
                    projectCreator = projectCreator,
                    trigger = PreviewStartTrigger.TOOL_WINDOW,
                )
            }
        }
    }

    private fun runInBackground(task: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.executeOnPooledThread(task)
            return
        }
        task()
    }

    private fun runOnUiThread(task: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.invokeLater(task, ModalityState.any())
            return
        }
        task()
    }

    private fun refreshProjectRoot(projectPath: String) {
        runCatching {
            val root = Path.of(projectPath).toAbsolutePath().normalize().toFile()
            val rootVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root)
            rootVirtualFile?.refresh(true, true)
        }
    }

    private fun openDefaultIndexInEditor(project: Project, projectPath: String) {
        if (ApplicationManager.getApplication() == null) {
            return
        }
        val indexPath = runCatching {
            Path.of(projectPath)
                .toAbsolutePath()
                .normalize()
                .resolve("docs")
                .resolve("index.md")
                .toString()
        }.getOrNull() ?: return
        val fileSystem = LocalFileSystem.getInstance()
        val virtualFile = fileSystem.findFileByPath(indexPath)
            ?: fileSystem.refreshAndFindFileByPath(indexPath)
            ?: return
        runCatching {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }
    }

    private fun registerEditorSelectionSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        lifecycleDisposable: Disposable,
    ) {
        project.messageBus.connect(lifecycleDisposable).subscribe(
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
        lifecycleDisposable: Disposable,
    ) {
        typingListenerRegistrar(
            project,
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    lastTypingTimestampByProject[project.locationHash] = System.currentTimeMillis()
                    syncEngine(project.locationHash).recordDocumentChange(event.document, event)
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
            lifecycleDisposable,
        )
    }

    private fun registerPreviewAnchorInvalidation(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        lifecycleDisposable: Disposable,
    ) {
        previewContent.setContentReloadListener {
            val engine = syncEngine(project.locationHash)
            engine.invalidateAnchors()
            engine.resetSyncState()
            triggerScrollRestorationAfterReload(project, runtimeService, previewContent)
        }
        val componentListener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent?) {
                syncEngine(project.locationHash).invalidateAnchors()
            }
        }
        previewContent.component.addComponentListener(componentListener)
        Disposer.register(lifecycleDisposable, Disposable {
            previewContent.component.removeComponentListener(componentListener)
            previewContent.setContentReloadListener(null)
        })
    }

    private fun triggerScrollRestorationAfterReload(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
    ) {
        if (!isPreviewModeActive(project)) {
            return
        }
        if (!runtimeService.isRuntimeRunning()) {
            return
        }

        val selectedPath = activeEditorPathProvider(project) ?: return
        if (!runtimeService.isPreviewEligibleMarkdownPath(selectedPath)) {
            return
        }

        val editors = runCatching {
            FileEditorManager.getInstance(project).selectedTextEditor
        }.getOrNull() ?: return

        val document = editors.document
        val visibleArea = editors.scrollingModel.visibleArea

        scheduleScrollSync(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            selectedPath = selectedPath,
            document = document,
            editorTopPx = visibleArea.y.toDouble(),
            viewportHeightPx = visibleArea.height.toDouble(),
            lineHeightPx = editors.lineHeight,
            rawDelta = 1,
        )
    }

    private fun registerEditorScrollSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        lifecycleDisposable: Disposable,
    ) {
        visibleAreaListenerRegistrar(
            project,
            /**
             * Tracks editor viewport movement for top-edge preview scroll sync.
             */
            object : VisibleAreaListener {
                /**
                 * Applies editor top-edge mapping to preview scroll position.
                 */
                override fun visibleAreaChanged(event: VisibleAreaEvent) {
                    val virtualFile = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
                    val selectedPath = virtualFile.path
                    val oldRectangle = event.oldRectangle ?: return
                    val newRectangle = event.newRectangle
                    val rawDelta = newRectangle.y - oldRectangle.y
                    if (rawDelta == 0) {
                        return
                    }

                    scheduleScrollSync(
                        project = project,
                        runtimeService = runtimeService,
                        previewContent = previewContent,
                        selectedPath = selectedPath,
                        document = event.editor.document,
                        editorTopPx = newRectangle.y.toDouble(),
                        viewportHeightPx = newRectangle.height.toDouble(),
                        lineHeightPx = event.editor.lineHeight,
                        rawDelta = rawDelta,
                    )
                }
            },
            lifecycleDisposable,
        )
    }

    private fun registerManualScrollSync(
        project: Project,
        previewContent: PreviewContent,
        lifecycleDisposable: Disposable,
    ) {
        previewContent.setManualScrollListener {
            userScrollLatchByProject[project.locationHash] = true
        }
        Disposer.register(lifecycleDisposable, Disposable {
            previewContent.setManualScrollListener(null)
        })
    }

    private fun registerTopicTreeReconciliationTriggers(
        project: Project,
        topicTreePanel: TopicTreeWorkspacePanel,
    ) {
        vfsBulkListenerRegistrar(
            project,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (project.isDisposed || events.isEmpty()) {
                        return
                    }
                    if (!shouldReconcileForVfsEvents(project, events)) {
                        return
                    }
                    runOnUiThread {
                        if (project.isDisposed) {
                            return@runOnUiThread
                        }
                        topicTreePanel.reconcileFromDisk()
                    }
                }
            },
            project,
        )
    }

    internal fun shouldReconcileForVfsEvents(
        project: Project,
        events: List<VFileEvent>,
    ): Boolean {
        if (events.isEmpty()) {
            return false
        }

        val coordinator = watcherCoordinatorFor(project)
        if (coordinator == null) {
            return events.any(::isPotentialTreeFileChangeEvent)
        }

        return events.any { event ->
            val change = toTopicTreeFileChange(event) ?: return@any false
            coordinator.evaluate(change).triggerReconciliation
        }
    }

    private fun watcherCoordinatorFor(project: Project): TopicTreeWatcherCoordinator? {
        val projectBasePath = project.basePath ?: return null
        val normalizedProjectRoot = runCatching {
            Path.of(projectBasePath).toAbsolutePath().normalize()
        }.getOrNull() ?: return null
        val defaultInstance = when (val instanceResult = defaultInstanceResolver(normalizedProjectRoot.toString())) {
            is TopicGatewayResult.Success -> instanceResult.value
            is TopicGatewayResult.Failure -> null
        }
        val docsDirPath = defaultInstance?.docsDirPath
            ?: normalizedProjectRoot.resolve("docs").normalize().toString()
        val configPaths = linkedSetOf<String>().apply {
            mkdocsConfigCandidateFileNames().forEach { fileName ->
                add(normalizedProjectRoot.resolve(fileName).toString().replace('\\', '/'))
            }
            findMkdocsConfig(normalizedProjectRoot)?.let { discoveredConfig ->
                add(discoveredConfig.toString().replace('\\', '/'))
            }
            defaultInstance?.configPath?.takeIf { it.isNotBlank() }?.let {
                add(it.replace('\\', '/'))
            }
        }

        return TopicTreeWatcherCoordinator(
            projectRootPath = normalizedProjectRoot.toString().replace('\\', '/'),
            docsDirPath = docsDirPath.replace('\\', '/'),
            configPaths = configPaths,
        )
    }

    private fun toTopicTreeFileChange(event: VFileEvent): TopicTreeFileChange? {
        return when (event) {
            is VFileCreateEvent -> TopicTreeFileChange(
                kind = WatcherEventKind.CREATE,
                path = event.path,
            )

            is VFileContentChangeEvent -> TopicTreeFileChange(
                kind = WatcherEventKind.UPDATE,
                path = event.path,
            )

            is VFileDeleteEvent -> TopicTreeFileChange(
                kind = WatcherEventKind.DELETE,
                path = event.path,
            )

            is VFileMoveEvent -> TopicTreeFileChange(
                kind = WatcherEventKind.MOVE,
                path = "${event.oldParent.path}/${event.file.name}",
                newPath = event.path,
            )

            is VFilePropertyChangeEvent -> {
                if (event.propertyName != VirtualFile.PROP_NAME) {
                    return null
                }
                val oldName = event.oldValue as? String ?: return null
                val newName = event.newValue as? String ?: return null
                val newPath = event.path
                val oldPath = if (newPath.endsWith("/$newName")) {
                    newPath.removeSuffix("/$newName") + "/$oldName"
                } else {
                    newPath
                }
                TopicTreeFileChange(
                    kind = WatcherEventKind.RENAME,
                    path = oldPath,
                    newPath = newPath,
                )
            }

            else -> null
        }
    }

    private fun isPotentialTreeFileChangeEvent(event: VFileEvent): Boolean {
        val normalizedPath = event.path.replace('\\', '/')
        if (isMkdocsConfigPath(normalizedPath)) {
            return true
        }
        val newPath = when (event) {
            is VFileMoveEvent -> event.path
            is VFilePropertyChangeEvent -> event.path
            else -> null
        }?.replace('\\', '/')
        return normalizedPath.endsWith(".md") || (newPath?.endsWith(".md") == true) || (newPath?.let(::isMkdocsConfigPath) == true)
    }

    internal fun applyPreviewRoute(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
    ): Boolean {
        if (!isPreviewModeActive(project)) {
            return false
        }
        if (!runtimeService.isPreviewEligibleMarkdownPath(selectedPath)) {
            return false
        }
        val previousUrl = runtimeService.currentPreviewUrl()
        val applied = runtimeService.dispatchPreviewForSelectedFileWithRetry(
            selectedPath = selectedPath,
            source = PreviewRouteIntentSource.TOOL_WINDOW_SELECTION,
            forceReload = true,
            loadUrl = { url, forceReload ->
                if (shouldLoadLivePreview(project)) {
                    val browserService = previewBrowserServiceResolver(project)
                    if (browserService != null) {
                        browserService.loadUrl(url, forceReload)
                    } else {
                        previewContent.loadUrl(url)
                    }
                }
            },
            shouldRetry = {
                if (project.isDisposed || !isPreviewModeActive(project)) {
                    false
                } else {
                    val activePath = runCatching { activeEditorPathProvider(project) }.getOrNull()
                        ?: return@dispatchPreviewForSelectedFileWithRetry true
                    isSamePath(activePath, selectedPath)
                }
            },
        )
        if (!applied) {
            return false
        }
        val updatedUrl = runtimeService.currentPreviewUrl()

        if (updatedUrl != previousUrl) {
            syncEngine(project.locationHash).invalidateAnchors()
        }
        return true
    }

    internal fun refreshCurrentPreviewForDocsSave(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        selectedPath: String,
    ): Boolean {
        if (!isPreviewModeActive(project)) {
            return false
        }
        if (!runtimeService.isRuntimeRunning()) {
            return false
        }

        if (!runtimeService.isPreviewEligibleMarkdownPath(selectedPath)) {
            return false
        }

        if (runtimeService.currentPreviewUrl().isNullOrBlank()) {
            return false
        }
        syncEngine(project.locationHash).invalidateAnchors()
        return true
    }

    internal fun scheduleTypingRefresh(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
        document: Document? = null,
    ): Boolean {
        if (!isPreviewModeActive(project)) {
            return false
        }
        if (!runtimeService.isPreviewEligibleMarkdownPath(selectedPath)) {
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
                requestMapRebuildForActiveEditor(project, previewContent, document)
            }
        }

        return true
    }

    /**
     * Schedules smooth preview scroll from editor-visible area events.
     * Mapping rule: editor top-edge position maps directly to preview top-edge position.
     */
    internal fun scheduleScrollSync(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        previewContent: PreviewContent,
        selectedPath: String,
        document: Document,
        editorTopPx: Double,
        viewportHeightPx: Double,
        lineHeightPx: Int,
        rawDelta: Int,
    ): Boolean {
        if (!isPreviewModeActive(project)) {
            return false
        }
        if (!runtimeService.isRuntimeRunning() ||
            !runtimeService.isPreviewEligibleMarkdownPath(selectedPath) ||
            rawDelta == 0
        ) {
            return false
        }
        if (selectedPath != activeEditorPathProvider(project)) {
            return false
        }
        val projectKey = project.locationHash
        val userActive = userScrollLatchByProject[projectKey] ?: false
        val lastTop = lastSyncedEditorTopByProject[projectKey] ?: Double.NaN

        if (userActive) {
            // Latch Logic: If user is interacting with preview, only break the latch if editor moves significantly (> 3 lines)
            val threshold = (lineHeightPx * 3).toDouble()
            if (!lastTop.isNaN() && abs(editorTopPx - lastTop) < threshold) {
                 return false
            }
            // Editor moved significantly, reset latch
            userScrollLatchByProject[projectKey] = false
        }

        lastSyncedEditorTopByProject[projectKey] = editorTopPx

        val lastTyping = lastTypingTimestampByProject[project.locationHash] ?: 0L
        if (System.currentTimeMillis() - lastTyping < typingScrollGuardMs) {
            return false
        }

        val engine = syncEngine(projectKey)
        val scrollCommand = engine.onEditorScroll(
            document = document,
            lineHeightPx = lineHeightPx.coerceAtLeast(1),
            editorScrollTopPx = editorTopPx,
            editorViewportHeightPx = viewportHeightPx,
            loadPreviewSnapshot = { callback ->
                previewContent.requestDomSnapshot(callback)
            },
        )
        if (scrollCommand != null) {
            previewContent.scrollToY(scrollCommand.previewY, scrollCommand.syncToken)
        }

        return true
    }

    private fun isSamePath(left: String, right: String): Boolean {
        val normalizedLeft = normalizePathForComparison(left)
        val normalizedRight = normalizePathForComparison(right)
        return if (isWindows()) {
            normalizedLeft.equals(normalizedRight, ignoreCase = true)
        } else {
            normalizedLeft == normalizedRight
        }
    }

    private fun normalizePathForComparison(path: String): String {
        return runCatching {
            Path.of(path).toAbsolutePath().normalize().toString()
        }.getOrElse {
            path.replace('\\', '/')
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)

    private fun saveDocumentForTypingRefresh(document: Document?) {
        document ?: return
        runCatching {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    private fun requestMapRebuildForActiveEditor(
        project: Project,
        previewContent: PreviewContent,
        document: Document?,
    ) {
        document ?: return
        val editor = runCatching { FileEditorManager.getInstance(project).selectedTextEditor }.getOrNull() ?: return
        if (editor.document !== document) {
            return
        }
        syncEngine(project.locationHash).requestMapRebuild(
            document = document,
            lineHeightPx = editor.lineHeight.coerceAtLeast(1),
            loadPreviewSnapshot = { callback ->
                previewContent.requestDomSnapshot(callback)
            },
        )
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
        topicTreeUiServicesByProject[project.locationHash] = uiService
        topicTreeControllersByProject[project.locationHash] = TopicTreeControllers(
            actionController = actionControllerFactory(uiService, recoveryPresenter),
            dragDropController = dragDropControllerFactory(uiService, recoveryPresenter),
            failureRecoveryPresenter = recoveryPresenter,
            instanceSwitchCoordinator = instanceSwitchCoordinatorFactory(uiService),
            instanceRegistryPort = instanceRegistryPort,
            uiService = uiService,
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

        val scopedInstance = resolveScopedStartupInstance(project, projectRoot)

        val docsDirPath = scopedInstance
            ?.let { instance -> runCatching { Path.of(instance.docsDirPath).toAbsolutePath().normalize() }.getOrNull() }
            ?: projectRoot.resolve("docs").normalize()

        val configDocument = scopedInstance
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
                navPresent = false,
            )
        val docsMarkdownPaths = collectDocsMarkdownPaths(docsDirPath)
        val startupState = runCatching {
            startupReconciliationResolver(project).reconcile(
                config = configDocument,
                docsMarkdownPaths = docsMarkdownPaths,
                projectId = project.locationHash,
                instanceId = scopedInstance?.instanceId ?: "default",
            )
        }.getOrNull() ?: return null

        // Keep orchestrator config IDs aligned with the currently rendered tree source.
        val hydratedConfig = configDocument.copy(nav = startupState.nodes)
        hydrateAggregateFromStartupConfig(
            project = project,
            treeId = scopedInstance?.instanceId ?: "default",
            instanceId = scopedInstance?.instanceId ?: "default",
            configDocument = hydratedConfig,
        )

        return startupState
    }

    private fun resolveScopedStartupInstance(project: Project, projectRoot: Path): TopicInstanceRef? {
        val registry = topicTreeControllers(project)?.instanceRegistryPort
        if (registry == null) {
            return when (val result = defaultInstanceResolver(projectRoot.toString())) {
                is TopicGatewayResult.Success -> result.value
                is TopicGatewayResult.Failure -> null
            }
        }

        val discoveredDefault = when (val result = registry.discoverDefaultInstance(projectRoot.toString())) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> null
        }
        val activeInstance = when (val result = registry.activeInstance()) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> null
        }

        val usableActive = activeInstance?.takeIf(::isUsableScopedInstance)
        if (usableActive != null) {
            return usableActive
        }

        if (discoveredDefault != null) {
            runCatching {
                registry.selectActiveInstance(discoveredDefault.instanceId)
            }
            return discoveredDefault
        }

        return activeInstance
    }

    private fun isUsableScopedInstance(instance: TopicInstanceRef): Boolean {
        return runCatching {
            Files.exists(Path.of(instance.configPath).toAbsolutePath().normalize())
        }.getOrDefault(false)
    }

    private fun hydrateAggregateFromStartupConfig(
        project: Project,
        treeId: String,
        instanceId: String,
        configDocument: MkDocsConfigDocument,
    ) {
        val uiService = topicTreeUiServicesByProject[project.locationHash] as? TopicTreeUiServiceImpl
            ?: return
        uiService.hydrateTreeFromConfig(
            treeId = treeId,
            instanceId = instanceId,
            config = configDocument,
        )
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
            project = project,
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
    val uiService: TopicTreeUiService? = null,
)

data class PreviewScrollMetrics(
    val scrollY: Double,
    val maxScrollY: Double,
)

data class PreviewDomAnchor(
    val id: String?,
    val type: AnchorType,
    val level: Int?,
    val top: Double,
    val bottom: Double,
    val normText: String,
)

data class PreviewDomSnapshot(
    val maxScrollY: Double,
    val anchors: List<PreviewDomAnchor>,
)

private data class ShellContent(
    val panel: JComponent,
    val splitter: OnePixelSplitter?,
)

private fun hasMkdocsConfigInProjectRoot(project: Project): Boolean {
    val basePath = project.basePath ?: return true
    val root = runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() ?: return true
    if (!Files.exists(root) || !Files.isDirectory(root)) {
        // Keep tests and non-materialized projects on the previous startup path.
        return true
    }
    return findMkdocsConfig(root) != null
}

private fun invalidateMkdocsConfigCacheForProject(project: Project) {
    val basePath = project.basePath ?: return
    val root = runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() ?: return
    invalidateMkdocsConfigCache(root)
}

/**
 * Contract for the preview surface embedded in the Authord tool window.
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
     * Loads setup page when no configuration file exists and forwards create action events.
     */
    fun loadSetupPage(onProjectCreate: (String) -> Unit) = Unit

    /**
     * Scrolls preview to a normalized vertical progress position.
     *
     * @param progress value within [0.0, 1.0] where 0 is top and 1 is bottom.
     */
    fun scrollToProgress(progress: Double) = Unit

    /**
     * Scrolls preview to an absolute Y coordinate in CSS pixels.
     */
    fun scrollToY(y: Double, syncToken: Long? = null) = Unit

    /**
     * Requests the current preview scroll metrics.
     */
    fun requestScrollMetrics(callback: (PreviewScrollMetrics?) -> Unit) {
        callback(null)
    }

    /**
     * Requests typed anchor snapshot from the preview DOM.
     */
    fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        callback(null)
    }

    /**
     * Registers listener invoked when preview content is reloaded in-place.
     */
    fun setContentReloadListener(listener: (() -> Unit)?) = Unit

    /**
     * Registers listener invoked when user manually scrolls the preview.
     */
    fun setManualScrollListener(listener: ((Double) -> Unit)?) = Unit

    /**
     * True when preview supports anchor-aware editor-to-preview synchronization wiring.
     */
    fun supportsPreviewEditorSync(): Boolean = false

    /**
     * True when setup mode should render a native Swing setup panel.
     */
    fun prefersSetupPanel(): Boolean = false

    /**
     * Releases preview resources owned by this surface.
     */
    fun dispose() = Unit
}

private class DeferredPreviewContent(
    private val syncCapable: Boolean,
    private val delegateFactory: () -> PreviewContent,
) : PreviewContent {
    private val hostPanel = JPanel(BorderLayout())
    @Volatile private var delegate: PreviewContent? = null
    @Volatile private var contentReloadListener: (() -> Unit)? = null
    @Volatile private var manualScrollListener: ((Double) -> Unit)? = null

    override val component: JComponent = hostPanel

    override fun supportsPreviewEditorSync(): Boolean = syncCapable

    override fun prefersSetupPanel(): Boolean = false

    override fun loadUrl(url: String) {
        ensureDelegateAttached().loadUrl(url)
    }

    override fun loadSetupPage(onProjectCreate: (String) -> Unit) {
        hostPanel.removeAll()
        hostPanel.add(SetupPanel(onProjectCreate), BorderLayout.CENTER)
        hostPanel.revalidate()
        hostPanel.repaint()
    }

    override fun scrollToProgress(progress: Double) {
        delegate?.scrollToProgress(progress)
    }

    override fun scrollToY(y: Double, syncToken: Long?) {
        delegate?.scrollToY(y, syncToken)
    }

    override fun requestScrollMetrics(callback: (PreviewScrollMetrics?) -> Unit) {
        val currentDelegate = delegate
        if (currentDelegate == null) {
            callback(null)
            return
        }
        currentDelegate.requestScrollMetrics(callback)
    }

    override fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        val currentDelegate = delegate
        if (currentDelegate == null) {
            callback(null)
            return
        }
        currentDelegate.requestDomSnapshot(callback)
    }

    override fun setContentReloadListener(listener: (() -> Unit)?) {
        contentReloadListener = listener
        delegate?.setContentReloadListener(listener)
    }

    override fun setManualScrollListener(listener: ((Double) -> Unit)?) {
        manualScrollListener = listener
        delegate?.setManualScrollListener(listener)
    }

    private fun ensureDelegateAttached(): PreviewContent {
        delegate?.let { existing ->
            attachDelegateComponent(existing)
            return existing
        }

        synchronized(this) {
            delegate?.let { existing ->
                attachDelegateComponent(existing)
                return existing
            }

            val created = delegateFactory()
            delegate = created
            created.setContentReloadListener(contentReloadListener)
            created.setManualScrollListener(manualScrollListener)
            attachDelegateComponent(created)
            return created
        }
    }

    private fun attachDelegateComponent(delegate: PreviewContent) {
        if (hostPanel.componentCount == 1 && hostPanel.getComponent(0) === delegate.component) {
            return
        }
        hostPanel.removeAll()
        hostPanel.add(delegate.component, BorderLayout.CENTER)
        hostPanel.revalidate()
        hostPanel.repaint()
    }

    override fun dispose() {
        synchronized(this) {
            delegate?.dispose()
            delegate = null
            contentReloadListener = null
            manualScrollListener = null
            hostPanel.removeAll()
            hostPanel.revalidate()
            hostPanel.repaint()
        }
    }
}

private class JcefPreviewContent(
    private val setupPageRenderer: SetupPageRenderer = SetupPageRenderer(),
    private val onMainFrameLoadEnd: (() -> Unit)? = null,
) : PreviewContent {
    private val browser = JBCefBrowser()
    private val metricsQuery = JBCefJSQuery.create(browser)
    private val domSnapshotQuery = JBCefJSQuery.create(browser)
    private val domMutationQuery = JBCefJSQuery.create(browser)
    private val manualScrollQuery = JBCefJSQuery.create(browser)
    private val setupProjectCreateQuery = JBCefJSQuery.create(browser)
    @Volatile private var pendingMetricsCallback: ((PreviewScrollMetrics?) -> Unit)? = null
    @Volatile private var pendingDomSnapshotCallback: ((PreviewDomSnapshot?) -> Unit)? = null
    @Volatile private var setupProjectCreateHandler: ((String) -> Unit)? = null
    @Volatile private var contentReloadListener: (() -> Unit)? = null
    @Volatile private var manualScrollListener: ((Double) -> Unit)? = null

    override val component: JComponent = browser.component

    override fun supportsPreviewEditorSync(): Boolean = true

    override fun prefersSetupPanel(): Boolean = true

    init {
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

        domMutationQuery.addHandler {
            val listener = contentReloadListener ?: return@addHandler null
            ApplicationManager.getApplication().invokeLater(listener, ModalityState.any())
            null
        }

        manualScrollQuery.addHandler { payload ->
            val listener = manualScrollListener ?: return@addHandler null
            val y = payload.toDoubleOrNull() ?: return@addHandler null
            ApplicationManager.getApplication().invokeLater(
                { listener(y) },
                ModalityState.any(),
            )
            null
        }

        setupProjectCreateQuery.addHandler { payload ->
            val handler = setupProjectCreateHandler ?: return@addHandler null
            val decoded = runCatching {
                URLDecoder.decode(payload.orEmpty(), StandardCharsets.UTF_8)
            }.getOrDefault(payload.orEmpty())
            val projectName = decoded.trim()
            ApplicationManager.getApplication().invokeLater(
                { handler(projectName) },
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
                    onMainFrameLoadEnd?.invoke()
                    injectScrollPersistenceScript(browser)
                    injectDomMutationObservers(browser)
                    injectManualScrollObserver(browser)
                    val listener = contentReloadListener ?: return
                    ApplicationManager.getApplication().invokeLater(listener, ModalityState.any())
                }
            },
            browser.cefBrowser,
        )

        browser.jbCefClient.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    val decision = PreviewNavigationPolicy.decide(
                        url = request?.url,
                        isMainFrame = frame?.isMain != false,
                        userGesture = userGesture,
                        isRedirect = isRedirect,
                    )
                    val requestUrl = request?.url.orEmpty()
                    if (decision.openExternally && requestUrl.isNotBlank()) {
                        BrowserUtil.browse(requestUrl)
                    }
                    return !decision.allowInPreview
                }
            },
            browser.cefBrowser,
        )
    }

    /**
     * Injects JavaScript to preserve scroll position across LiveReload cycles.
     * Saves both scrollY and the nearest visible heading anchor before unload.
     */
    private fun injectScrollPersistenceScript(cefBrowser: CefBrowser?) {
        cefBrowser ?: return
        val script = """
            (function() {
                var SCROLL_KEY = '__authord_scrollY';
                var ANCHOR_KEY = '__authord_scrollAnchor';

                var savedAnchor = sessionStorage.getItem(ANCHOR_KEY);
                var savedY = parseFloat(sessionStorage.getItem(SCROLL_KEY) || '0');
                if (savedAnchor) {
                    var anchorElement = document.getElementById(savedAnchor);
                    if (anchorElement) {
                        setTimeout(function() {
                            anchorElement.scrollIntoView({ behavior: 'instant' });
                        }, 80);
                    } else if (!isNaN(savedY) && savedY > 0) {
                        setTimeout(function() {
                            window.scrollTo(0, savedY);
                        }, 80);
                    }
                } else if (!isNaN(savedY) && savedY > 0) {
                    setTimeout(function() {
                        window.scrollTo(0, savedY);
                    }, 80);
                }

                var saveState = function() {
                    if (window.__authordIsSyncing) {
                        return;
                    }
                    sessionStorage.setItem(SCROLL_KEY, String(window.scrollY));
                    var headings = document.querySelectorAll('h1[id],h2[id],h3[id],h4[id],h5[id],h6[id]');
                    var bestId = '';
                    for (var i = 0; i < headings.length; i++) {
                        var rect = headings[i].getBoundingClientRect();
                        if (rect.top >= 0 && rect.top < window.innerHeight * 0.5) {
                            bestId = headings[i].id;
                            break;
                        }
                        if (rect.top < 0) {
                            bestId = headings[i].id;
                        }
                    }
                    sessionStorage.setItem(ANCHOR_KEY, bestId);
                };

                window.addEventListener('scroll', saveState, { passive: true });
                window.addEventListener('beforeunload', saveState);
            })();
        """.trimIndent()
        cefBrowser.executeJavaScript(script, cefBrowser.url ?: "", 0)
    }

    private fun injectDomMutationObservers(cefBrowser: CefBrowser?) {
        cefBrowser ?: return
        val script = """
            (function() {
                if (window.__authordDomObserverInstalled) {
                    return;
                }
                window.__authordDomObserverInstalled = true;
                var queued = false;
                var notify = function() {
                    if (queued) return;
                    queued = true;
                    setTimeout(function() {
                        queued = false;
                        var payload = String(Date.now());
                        ${domMutationQuery.inject("payload")};
                    }, 100);
                };

                if (window.ResizeObserver) {
                    var resizeObserver = new ResizeObserver(function() {
                        notify();
                    });
                    resizeObserver.observe(document.documentElement);
                    if (document.body) {
                        resizeObserver.observe(document.body);
                    }
                }

                var mutationObserver = new MutationObserver(function(mutations) {
                    for (var i = 0; i < mutations.length; i++) {
                        var mutation = mutations[i];
                        if (mutation.type === "childList" || mutation.type === "attributes") {
                            notify();
                            return;
                        }
                    }
                });
                mutationObserver.observe(document.documentElement, {
                    subtree: true,
                    childList: true,
                    attributes: true
                });

                window.addEventListener("load", notify, { once: false });
                window.addEventListener("resize", notify, { passive: true });
                document.addEventListener("load", function(event) {
                    var target = event && event.target;
                    if (target && target.tagName && String(target.tagName).toLowerCase() === "img") {
                        notify();
                    }
                }, true);
            })();
        """.trimIndent()
        cefBrowser.executeJavaScript(script, cefBrowser.url ?: "", 0)
    }

    private fun injectManualScrollObserver(cefBrowser: CefBrowser?) {
        cefBrowser ?: return
        val script = """
            (function() {
                if (window.__authordScrollObserverInstalled) {
                    return;
                }
                window.__authordScrollObserverInstalled = true;

                var emitScroll = function() {
                    if (window.__authordIsSyncing) {
                        return;
                    }
                    var root = document.scrollingElement || document.documentElement || document.body;
                    if (!root) return;
                    var y = root.scrollTop || window.scrollY || 0;
                    ${manualScrollQuery.inject("y")};
                };

                // Debounce manual scroll events to avoid flooding the bridge
                var timer = null;
                window.addEventListener('scroll', function() {
                    if (window.__authordIsSyncing) {
                        return;
                    }
                    if (timer) {
                        clearTimeout(timer);
                    }
                    timer = setTimeout(function() {
                        emitScroll();
                    }, 50);
                }, { passive: true });
            })();
        """.trimIndent()
        cefBrowser.executeJavaScript(script, cefBrowser.url ?: "", 0)
    }

    /**
     * Loads preview content in the embedded Chromium browser.
     */
    override fun loadUrl(url: String) {
        browser.loadURL(withRefreshToken(url))
    }

    override fun loadSetupPage(onProjectCreate: (String) -> Unit) {
        setupProjectCreateHandler = onProjectCreate
        val html = setupPageRenderer.render(
            createProjectBridgeScript = setupProjectCreateQuery.inject("payload"),
        )
        loadHtml(html)
    }

    override fun setManualScrollListener(listener: ((Double) -> Unit)?) {
        manualScrollListener = listener
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
                
                // Calculate total scrollable height
                const totalHeight = Math.max(0, root.scrollHeight - window.innerHeight);
                
                // Fallback normalized sync:
                // `progress` is a top-edge ratio in [0..1], so map it directly to scrollable height.
                
                const target = totalHeight * targetProgress;
                
                window.__authordIsSyncing = true;
                if (window.__authordSyncResetTimer) {
                    clearTimeout(window.__authordSyncResetTimer);
                }
                window.__authordSyncResetTimer = setTimeout(function() {
                    window.__authordIsSyncing = false;
                }, 50);

                root.scrollTop = target;
            })();
        """.trimIndent()
        executeScript(script)
    }

    /**
     * Scrolls the embedded browser to an absolute document Y position.
     */
    override fun scrollToY(y: Double, syncToken: Long?) {
        val targetY = y.coerceAtLeast(0.0)
        val token = syncToken ?: -1L
        val script = """
            (function() {
                const rawTarget = Number(${targetY});
                const token = Number(${token});
                const root = document.scrollingElement || document.documentElement || document.body;
                if (!root) return;

                const maxScroll = Math.max(0, root.scrollHeight - window.innerHeight);
                const safeTarget = Math.min(Math.max(rawTarget, 0), maxScroll);
                
                window.__authordIsSyncing = true;
                if (window.__authordSyncResetTimer) {
                    clearTimeout(window.__authordSyncResetTimer);
                }
                window.__authordSyncResetTimer = setTimeout(function() {
                    window.__authordIsSyncing = false;
                }, 50);

                root.scrollTop = safeTarget;
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
     * Returns a typed DOM anchor snapshot used for deterministic piecewise interpolation.
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

                const normalizeText = function(input) {
                    const value = String(input || "");
                    const normalized = (value.normalize ? value.normalize("NFKC") : value).toLowerCase().trim();
                    if (!normalized) return "";
                    return normalized
                        .replace(/[`*_>#~!\[\](){}:;.,'"\\|+=-]/g, " ")
                        .replace(/\s+/g, " ")
                        .trim();
                };

                const basename = function(path) {
                    if (!path) return "";
                    const stripped = String(path).split("#")[0].split("?")[0];
                    const parts = stripped.split(/[\\/]/);
                    return parts.length ? parts[parts.length - 1] : stripped;
                };

                const resolveAnchorData = function(node) {
                    const tag = String(node.tagName || "").toLowerCase();
                    if (!tag) return null;

                    if (/^h[1-6]$/.test(tag)) {
                        return {
                            type: "H",
                            level: Number(tag.substring(1)),
                            id: String(node.getAttribute("id") || "").trim(),
                            text: normalizeText(node.textContent || "")
                        };
                    }
                    if (tag === "pre") {
                        const lines = String(node.textContent || "").split(/\n/).map(s => s.trim()).filter(Boolean);
                        return {
                            type: "CODE",
                            level: 0,
                            id: "",
                            text: normalizeText(lines[0] || node.textContent || "")
                        };
                    }
                    if (tag === "table") {
                        const headerCells = Array.from(node.querySelectorAll("thead th"));
                        const header = headerCells.map(function(cell) { return normalizeText(cell.textContent || ""); }).filter(Boolean).join(" ");
                        const fallback = normalizeText(node.textContent || "");
                        return {
                            type: "TABLE",
                            level: 0,
                            id: "",
                            text: header || fallback
                        };
                    }
                    if (tag === "img") {
                        const alt = normalizeText(node.getAttribute("alt") || "");
                        const src = basename(node.getAttribute("src") || "");
                        return {
                            type: "IMG",
                            level: 0,
                            id: "",
                            text: normalizeText((alt + " " + src).trim())
                        };
                    }
                    if (tag === "figure") {
                        const image = node.querySelector("img");
                        if (!image) return null;
                        const alt = normalizeText(image.getAttribute("alt") || "");
                        const src = basename(image.getAttribute("src") || "");
                        return {
                            type: "IMG",
                            level: 0,
                            id: "",
                            text: normalizeText((alt + " " + src).trim())
                        };
                    }
                    if (tag === "blockquote") {
                        return {
                            type: "BQ",
                            level: 0,
                            id: "",
                            text: normalizeText(node.textContent || "")
                        };
                    }
                    if (tag === "li") {
                        return {
                            type: "LI",
                            level: 0,
                            id: "",
                            text: normalizeText(node.textContent || "")
                        };
                    }
                    if (tag === "p") {
                        if (node.closest("li") || node.closest("blockquote")) {
                            return null;
                        }
                        return {
                            type: "P",
                            level: 0,
                            id: "",
                            text: normalizeText(node.textContent || "")
                        };
                    }
                    if (tag === "hr") {
                        return {
                            type: "HR",
                            level: 0,
                            id: "",
                            text: "hr"
                        };
                    }

                    return null;
                };

                const anchors = [];
                const nodes = document.querySelectorAll("h1,h2,h3,h4,h5,h6,p,pre,table,blockquote,li,img,figure,hr");
                for (const node of nodes) {
                    if (!node || !node.getBoundingClientRect) continue;
                    const rect = node.getBoundingClientRect();
                    const style = window.getComputedStyle(node);
                    if (!style) continue;
                    if (style.display === "none" || style.visibility === "hidden") continue;
                    if (rect.width <= 1 || rect.height <= 1) continue;

                    const resolved = resolveAnchorData(node);
                    if (!resolved) continue;
                    if (!resolved.text && resolved.type !== "HR") continue;

                    const top = Math.max(0, currentY + rect.top);
                    const bottom = Math.max(top + 1, currentY + rect.bottom);
                    const encodedId = encodeURIComponent(String(resolved.id || "").trim());
                    const encodedText = encodeURIComponent(String(resolved.text || "").trim());
                    const encodedType = encodeURIComponent(String(resolved.type || "").trim());
                    const level = Number(resolved.level || 0);
                    anchors.push([
                        encodedType,
                        String(level),
                        String(top),
                        String(bottom),
                        encodedId,
                        encodedText
                    ].join(","));
                }

                const payload = [String(maxScroll)].concat(anchors).join("|");
                ${domSnapshotQuery.inject("payload")};
            })();
        """.trimIndent()
        executeScript(script)
    }

    override fun setContentReloadListener(listener: (() -> Unit)?) {
        contentReloadListener = listener
    }

    private fun executeScript(script: String) {
        val url = browser.cefBrowser.url ?: "about:blank"
        browser.cefBrowser.executeJavaScript(script, url, 0)
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
        val anchors = segments.drop(1).mapNotNull { item ->
            if (item.isBlank()) {
                return@mapNotNull null
            }
            val parts = item.split(',', limit = 6)
            if (parts.size < 6) {
                return@mapNotNull null
            }
            val encodedType = parts[0]
            val level = parts[1].toIntOrNull()
            val top = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            val bottom = parts[3].toDoubleOrNull() ?: return@mapNotNull null
            val encodedId = parts[4]
            val encodedText = parts[5]
            val decodedType = runCatching {
                URLDecoder.decode(encodedType, StandardCharsets.UTF_8)
            }.getOrDefault(encodedType)
            val decodedId = runCatching {
                URLDecoder.decode(encodedId, StandardCharsets.UTF_8)
            }.getOrDefault(encodedId).trim().ifBlank { null }
            val decodedText = runCatching {
                URLDecoder.decode(encodedText, StandardCharsets.UTF_8)
            }.getOrDefault(encodedText)
            val resolvedType = runCatching {
                AnchorType.valueOf(decodedType.trim().uppercase())
            }.getOrNull() ?: return@mapNotNull null
            if (decodedText.isBlank() && resolvedType != AnchorType.HR) {
                return@mapNotNull null
            }

            PreviewDomAnchor(
                id = decodedId,
                type = resolvedType,
                level = level?.takeIf { it > 0 },
                top = top.coerceAtLeast(0.0),
                bottom = bottom.coerceAtLeast(top + 1.0),
                normText = decodedText,
            )
        }

        return PreviewDomSnapshot(
            maxScrollY = maxScrollY.coerceAtLeast(0.0),
            anchors = anchors,
        )
    }

    private fun withRefreshToken(url: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "${url}${separator}__authord_preview_ts=${System.currentTimeMillis()}"
    }

    private fun loadHtml(html: String) {
        val base64 = Base64.getEncoder().encodeToString(html.toByteArray(StandardCharsets.UTF_8))
        browser.loadURL("data:text/html;charset=utf-8;base64,$base64")
    }

    override fun dispose() {
        pendingMetricsCallback = null
        pendingDomSnapshotCallback = null
        setupProjectCreateHandler = null
        contentReloadListener = null
        manualScrollListener = null
        runCatching { metricsQuery.dispose() }
        runCatching { domSnapshotQuery.dispose() }
        runCatching { domMutationQuery.dispose() }
        runCatching { manualScrollQuery.dispose() }
        runCatching { setupProjectCreateQuery.dispose() }
        runCatching { browser.dispose() }
    }
}

internal data class PreviewNavigationDecision(
    val allowInPreview: Boolean,
    val openExternally: Boolean,
)

/**
 * Encapsulates preview navigation policy for local-only in-pane browsing.
 */
internal object PreviewNavigationPolicy {
    fun decide(
        url: String?,
        isMainFrame: Boolean,
        userGesture: Boolean,
        isRedirect: Boolean,
    ): PreviewNavigationDecision {
        if (!isMainFrame) {
            return PreviewNavigationDecision(allowInPreview = true, openExternally = false)
        }

        val normalizedUrl = url?.trim().orEmpty()
        if (normalizedUrl.isBlank()) {
            return PreviewNavigationDecision(allowInPreview = true, openExternally = false)
        }
        if (isAlwaysAllowedSchemeUrl(normalizedUrl) || isLocalUrl(normalizedUrl)) {
            return PreviewNavigationDecision(allowInPreview = true, openExternally = false)
        }

        return PreviewNavigationDecision(
            allowInPreview = false,
            openExternally = userGesture || isRedirect,
        )
    }

    private fun isAlwaysAllowedSchemeUrl(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase().orEmpty() }.getOrDefault("")
        return scheme in setOf("about", "data", "file", "chrome", "devtools")
    }

    internal fun isLocalUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") {
            return false
        }

        val host = uri.host?.lowercase() ?: return false
        return host == "localhost" ||
            host == "0.0.0.0" ||
            host == "::1" ||
            host.startsWith("127.")
    }
}

private class HtmlPreviewContent : PreviewContent {
    private val editorPane = JEditorPane("text/html", "").apply {
        isEditable = false
        text = """
            <html>
              <body style="font-family:sans-serif;padding:12px;">
                <p>Authord preview will load here after startup.</p>
              </body>
            </html>
        """.trimIndent()
    }
    @Volatile private var contentReloadListener: (() -> Unit)? = null

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
        contentReloadListener?.invoke()
    }

    override fun loadSetupPage(onProjectCreate: (String) -> Unit) {
        editorPane.text = """
            <html>
              <body style="font-family:sans-serif;padding:12px;">
                <h3>Create Project</h3>
                <p>No MkDocs configuration file was found in this project.</p>
                <p>Embedded browser is unavailable in this runtime, so setup form is not interactive.</p>
                <p>Create a project manually and reopen the tool window.</p>
              </body>
            </html>
        """.trimIndent()
        contentReloadListener?.invoke()
    }

    override fun setContentReloadListener(listener: (() -> Unit)?) {
        contentReloadListener = listener
    }
}

internal fun createDefaultPreviewContent(
    onMainFrameLoadEnd: (() -> Unit)?,
): PreviewContent {
    val jcefSupported = JBCefApp.isSupported()
    return DeferredPreviewContent(syncCapable = jcefSupported) {
        if (jcefSupported) {
            JcefPreviewContent(onMainFrameLoadEnd = onMainFrameLoadEnd)
        } else {
            HtmlPreviewContent()
        }
    }
}

internal fun createDefaultPreviewContent(): PreviewContent {
    return createDefaultPreviewContent(onMainFrameLoadEnd = null)
}
