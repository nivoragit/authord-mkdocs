package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.abs

internal const val AUTHORD_PREVIEW_EDITOR_TYPE_ID = "authord-preview-editor"

internal fun isAuthordPreviewEligible(projectBasePath: String?, filePath: String): Boolean {
    val basePath = projectBasePath ?: return false
    return isMarkdownPath(filePath) &&
        hasConfigFile(basePath) &&
        isUnderProject(basePath, filePath)
}

/**
 * Replaces the default Markdown split preview with an Authord-backed preview.
 */
class AuthordMarkdownSplitEditorProvider : TextEditorWithPreviewProvider, DumbAware {
    constructor() : super(AuthordMarkdownPreviewFileEditorProvider())

    internal constructor(previewProvider: FileEditorProvider) : super(previewProvider)

    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
        require(secondEditor is AuthordMarkdownPreviewFileEditor) {
            "Secondary editor should be AuthordMarkdownPreviewFileEditor"
        }
        return AuthordMarkdownEditorWithPreview(firstEditor, secondEditor)
    }

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS
}

private class AuthordMarkdownPreviewFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return isAuthordPreviewEligible(project.basePath, file.path)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return AuthordMarkdownPreviewFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = AUTHORD_PREVIEW_EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class AuthordMarkdownEditorWithPreview(
    private val textEditor: TextEditor,
    private val previewEditor: AuthordMarkdownPreviewFileEditor,
) : TextEditorWithPreview(
    textEditor,
    previewEditor,
    "Authord Preview",
    preferredAuthordSplitLayout(textEditor.editor.project) ?: Layout.SHOW_EDITOR_AND_PREVIEW,
) {
    init {
        previewEditor.bindSourceEditor(textEditor.editor)
        previewEditor.refreshForSelectedFile(autoStart = false, trigger = PreviewStartTrigger.ACTION)
    }

    override fun setState(state: FileEditorState) {
        super.setState(state)
        applySharedLayoutPreference()
    }

    override fun setLayout(layout: Layout) {
        super.setLayout(layout)
        storeAuthordSplitLayout(textEditor.editor.project, layout)
    }

    override fun createRightToolbarActionGroup(): ActionGroup {
        return DefaultActionGroup(
            object : DumbAwareAction(
                "Restart Authord Preview",
                "Restart Authord runtime and refresh preview",
                AllIcons.Actions.Refresh,
            ) {
                override fun actionPerformed(event: AnActionEvent) {
                    previewEditor.restartPreviewAndRefresh()
                }
            },
            object : DumbAwareAction(
                "Toggle Authord Treeview",
                "Show or hide the Authord Treeview tool window",
                AllIcons.Nodes.Folder,
            ) {
                override fun actionPerformed(event: AnActionEvent) {
                    val project = event.project ?: textEditor.editor.project ?: return
                    toggleAuthordToolWindow(project)
                }
            },
        )
    }

    internal fun refreshPreviewAfterRuntimeStart(trigger: PreviewStartTrigger): Boolean {
        return previewEditor.refreshForSelectedFile(autoStart = false, trigger = trigger)
    }

    private fun applySharedLayoutPreference() {
        val project = textEditor.editor.project ?: return
        val preferred = preferredAuthordSplitLayout(project) ?: return
        val current = runCatching { super.getLayout() }.getOrNull() ?: return
        if (preferred != current) {
            super.setLayout(preferred)
        }
    }
}

internal fun preferredAuthordSplitLayout(project: Project?): TextEditorWithPreview.Layout? {
    val activeProject = project ?: return null
    val service = runCatching { activeProject.getService(AuthordSplitEditorLayoutStateService::class.java) }.getOrNull()
        ?: return null
    return service.preferredLayout()
}

internal fun storeAuthordSplitLayout(project: Project?, layout: TextEditorWithPreview.Layout) {
    val activeProject = project ?: return
    runCatching { activeProject.getService(AuthordSplitEditorLayoutStateService::class.java) }
        .getOrNull()
        ?.setPreferredLayout(layout)
}

internal fun parseAuthordSplitLayoutName(stored: String?): TextEditorWithPreview.Layout? {
    val normalized = stored?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return null
    }
    return TextEditorWithPreview.Layout.entries.firstOrNull { it.name == normalized }
}

internal class AuthordMarkdownPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val browserServiceResolver: (Project) -> MkDocsPreviewBrowserService? = { currentProject ->
        runCatching { currentProject.getService(MkDocsPreviewBrowserService::class.java) }.getOrNull()
    },
    private val resultPresenter: (Project, String, Boolean) -> Unit = ::presentPreviewResult,
    previewContentFactory: () -> PreviewContent = ::createDefaultPreviewContent,
    private val delayedInvoker: (delayMillis: Long, task: () -> Unit) -> Unit = delayedInvoker@{ delayMillis, task ->
        val app = ApplicationManager.getApplication()
        if (app == null) {
            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@delayedInvoker
            }
            task()
            return@delayedInvoker
        }

        app.executeOnPooledThread {
            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@executeOnPooledThread
            }
            app.invokeLater(task, ModalityState.any())
        }
    },
) : UserDataHolderBase(), FileEditor {
    private val runtimeService = runtimeServiceResolver(project)
    private val browserService = browserServiceResolver(project)
    private val previewOwnerId = "${file.path}#${System.identityHashCode(this)}"
    private val previewContent = browserService?.previewContentForOwner(
        ownerKind = PreviewOwnerKind.SPLIT_EDITOR,
        ownerId = previewOwnerId,
    ) ?: previewContentFactory()
    private val scrollSyncEngine = MkdocsScrollSyncEngine(delayedInvoker = delayedInvoker)
    private var standaloneLastLoadedUrl: String? = null
    @Volatile
    private var sourceEditor: Editor? = null
    @Volatile
    private var previewScrollLatchedByUser: Boolean = false
    @Volatile
    private var lastSyncedEditorTopPx: Double = Double.NaN
    @Volatile
    private var lastTypingTimestampMs: Long = 0L
    private val typingGeneration = AtomicInteger(0)
    private val typingRefreshDelaysMs = listOf(2000L)
    private val typingScrollGuardMs: Long = 800L

    init {
        registerEditorListeners()
        registerPreviewSyncListeners()
        runtimeService.currentPreviewUrl()?.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
    }

    override fun getComponent(): JComponent = previewContent.component

    override fun getPreferredFocusedComponent(): JComponent = previewContent.component

    override fun getName(): String = "Authord Preview"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = !project.isDisposed && file.isValid

    override fun selectNotify() {
        browserService?.activatePreviewOwner(
            ownerKind = PreviewOwnerKind.SPLIT_EDITOR,
            ownerId = previewOwnerId,
        )
        refreshForSelectedFile(autoStart = false, trigger = PreviewStartTrigger.ACTION)
    }

    override fun deselectNotify() {
        browserService?.deactivatePreviewOwner(
            ownerKind = PreviewOwnerKind.SPLIT_EDITOR,
            ownerId = previewOwnerId,
        )
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() {
        browserService?.releasePreviewOwner(
            ownerKind = PreviewOwnerKind.SPLIT_EDITOR,
            ownerId = previewOwnerId,
        )
    }

    internal fun bindSourceEditor(editor: Editor) {
        sourceEditor = editor
        requestMapRebuildForBoundEditor(editor.document)
    }

    internal fun restartPreviewAndRefresh() {
        if (!hasConfigFile()) {
            resultPresenter(project, missingConfigMessage(), false)
            return
        }
        runtimeService.restartPreviewAsync(PreviewStartTrigger.ACTION) { restartResult ->
            resultPresenter(project, formatPreviewResultMessage(restartResult), restartResult.success)
            if (!restartResult.success) {
                return@restartPreviewAsync
            }
            standaloneLastLoadedUrl = null
            val dispatched = dispatchPreviewForCurrentFile(PreviewRouteIntentSource.SPLIT_EDITOR)
            if (!dispatched) {
                val resolvedUrl = restartResult.previewUrl.ifBlank {
                    runtimeService.currentPreviewUrl().orEmpty()
                }
                if (resolvedUrl.isNotBlank()) {
                    loadUrlIfChanged(resolvedUrl, forceReload = true)
                }
            }
        }
    }

    internal fun refreshForSelectedFile(autoStart: Boolean, trigger: PreviewStartTrigger): Boolean {
        if (project.isDisposed || !file.isValid) {
            return false
        }
        if (!hasConfigFile()) {
            if (autoStart) {
                resultPresenter(project, missingConfigMessage(), false)
            }
            return false
        }

        if (autoStart && !runtimeService.isRuntimeRunning()) {
            val application = ApplicationManager.getApplication()
            if (application == null) {
                val startResult = runtimeService.startPreview(trigger)
                if (!startResult.success) {
                    resultPresenter(project, formatPreviewResultMessage(startResult), false)
                    return false
                }
                val dispatched = dispatchPreviewForCurrentFile(PreviewRouteIntentSource.SPLIT_EDITOR)
                if (!dispatched) {
                    val resolvedAfterStart = startResult.previewUrl.ifBlank {
                        runtimeService.currentPreviewUrl().orEmpty()
                    }
                    resolvedAfterStart.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
                    return resolvedAfterStart.isNotBlank()
                }
                return true
            } else {
                runtimeService.startPreviewAsync(trigger) { startResult ->
                    if (!startResult.success) {
                        resultPresenter(project, formatPreviewResultMessage(startResult), false)
                        return@startPreviewAsync
                    }
                    val dispatched = dispatchPreviewForCurrentFile(PreviewRouteIntentSource.SPLIT_EDITOR)
                    if (!dispatched) {
                        val resolvedAfterStart = startResult.previewUrl.ifBlank {
                            runtimeService.currentPreviewUrl().orEmpty()
                        }
                        resolvedAfterStart.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
                    }
                }
                return true
            }
        }

        val dispatched = dispatchPreviewForCurrentFile(PreviewRouteIntentSource.SPLIT_EDITOR)
        if (dispatched) {
            return true
        }
        val fallbackUrl = runtimeService.currentPreviewUrl()
        if (!fallbackUrl.isNullOrBlank()) {
            loadUrlIfChanged(fallbackUrl)
        }
        return fallbackUrl != null
    }

    private fun dispatchPreviewForCurrentFile(source: PreviewRouteIntentSource): Boolean {
        return runtimeService.dispatchPreviewForSelectedFileWithRetry(
            selectedPath = file.path,
            source = source,
            forceReload = true,
            loadUrl = { resolvedUrl, forceReload -> loadUrlIfChanged(resolvedUrl, forceReload) },
            shouldRetry = ::isFileStillSelectedForPreview,
        )
    }

    private fun isFileStillSelectedForPreview(): Boolean {
        if (project.isDisposed || !file.isValid) {
            return false
        }
        val selectedFiles = runCatching { FileEditorManager.getInstance(project).selectedFiles.toList() }.getOrNull()
            ?: return true
        val expected = comparablePath(file.path)
        return selectedFiles.any { selectedFile ->
            comparablePath(selectedFile.path) == expected
        }
    }

    private fun comparablePath(path: String): String {
        val normalized = path.replace('\\', '/')
        return if (SystemInfoRt.isFileSystemCaseSensitive) normalized else normalized.lowercase()
    }

    private fun loadUrlIfChanged(url: String, forceReload: Boolean = false) {
        if (browserService != null) {
            browserService.loadUrl(url, forceReload)
            return
        }
        if (!forceReload && standaloneLastLoadedUrl == url) {
            return
        }
        standaloneLastLoadedUrl = url
        previewContent.loadUrl(url)
    }

    private fun registerEditorListeners() {
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        val editor = sourceEditor ?: return
                        if (event.document !== editor.document) {
                            return
                        }
                        lastTypingTimestampMs = System.currentTimeMillis()
                        scrollSyncEngine.recordDocumentChange(event.document, event)
                        scheduleTypingRefresh(event.document)
                    }
                },
                this,
            )
        }
        runCatching {
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(
                object : VisibleAreaListener {
                    override fun visibleAreaChanged(event: VisibleAreaEvent) {
                        val editor = sourceEditor ?: return
                        if (event.editor !== editor) {
                            return
                        }
                        val oldRectangle = event.oldRectangle ?: return
                        val newRectangle = event.newRectangle
                        val rawDelta = newRectangle.y - oldRectangle.y
                        if (rawDelta == 0) {
                            return
                        }
                        scheduleScrollSync(
                            document = event.editor.document,
                            editorTopPx = newRectangle.y.toDouble(),
                            viewportHeightPx = newRectangle.height.toDouble(),
                            lineHeightPx = event.editor.lineHeight,
                            rawDelta = rawDelta,
                        )
                    }
                },
                this,
            )
        }
    }

    private fun registerPreviewSyncListeners() {
        previewContent.setContentReloadListener {
            scrollSyncEngine.invalidateAnchors()
            scrollSyncEngine.resetSyncState()
            triggerScrollRestorationAfterReload()
        }
        previewContent.setManualScrollListener {
            previewScrollLatchedByUser = true
        }
        (previewContent.component as? JPanel ?: previewContent.component).addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent?) {
                    scrollSyncEngine.invalidateAnchors()
                }
            },
        )
    }

    private fun triggerScrollRestorationAfterReload() {
        if (!runtimeService.isRuntimeRunning() || !isDocsMarkdownPath(file.path)) {
            return
        }
        val editor = sourceEditor ?: return
        val visibleArea = editor.scrollingModel.visibleArea
        scheduleScrollSync(
            document = editor.document,
            editorTopPx = visibleArea.y.toDouble(),
            viewportHeightPx = visibleArea.height.toDouble(),
            lineHeightPx = editor.lineHeight,
            rawDelta = 1,
        )
    }

    private fun scheduleTypingRefresh(document: Document) {
        if (!isDocsMarkdownPath(file.path)) {
            return
        }
        val generation = typingGeneration.incrementAndGet()
        typingRefreshDelaysMs.forEach { delay ->
            delayedInvoker(delay) {
                if (project.isDisposed || typingGeneration.get() != generation) {
                    return@delayedInvoker
                }
                saveDocumentForTypingRefresh(document)
                requestMapRebuildForBoundEditor(document)
            }
        }
    }

    private fun scheduleScrollSync(
        document: Document,
        editorTopPx: Double,
        viewportHeightPx: Double,
        lineHeightPx: Int,
        rawDelta: Int,
    ): Boolean {
        if (!runtimeService.isRuntimeRunning() || !isDocsMarkdownPath(file.path) || rawDelta == 0) {
            return false
        }

        if (previewScrollLatchedByUser) {
            val thresholdPx = (lineHeightPx * 3).toDouble()
            if (!lastSyncedEditorTopPx.isNaN() && abs(editorTopPx - lastSyncedEditorTopPx) < thresholdPx) {
                return false
            }
            previewScrollLatchedByUser = false
        }

        lastSyncedEditorTopPx = editorTopPx
        if (System.currentTimeMillis() - lastTypingTimestampMs < typingScrollGuardMs) {
            return false
        }

        val scrollCommand = scrollSyncEngine.onEditorScroll(
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

    private fun requestMapRebuildForBoundEditor(document: Document) {
        val editor = sourceEditor ?: return
        if (editor.document !== document) {
            return
        }
        scrollSyncEngine.requestMapRebuild(
            document = document,
            lineHeightPx = editor.lineHeight.coerceAtLeast(1),
            loadPreviewSnapshot = { callback ->
                previewContent.requestDomSnapshot(callback)
            },
        )
    }

    private fun saveDocumentForTypingRefresh(document: Document) {
        runCatching {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    private fun hasConfigFile(): Boolean {
        val basePath = project.basePath ?: return false
        return hasConfigFile(basePath)
    }

    private fun missingConfigMessage(): String {
        val projectPath = project.basePath ?: "<unknown>"
        return AuthordUiBundle.message("activation.error.configNotFound", projectPath)
    }

    private fun isDocsMarkdownPath(path: String): Boolean {
        return runtimeService.isPreviewEligibleMarkdownPath(path)
    }
}

internal fun refreshOpenAuthordMarkdownPreviews(
    project: Project,
    trigger: PreviewStartTrigger,
): Boolean {
    val manager = runCatching { com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project) }.getOrNull()
        ?: return false
    val editors = manager.selectedEditors.toList()
    var refreshed = false
    editors.forEach { editor ->
        refreshed = when (editor) {
            is AuthordMarkdownPreviewFileEditor -> {
                editor.refreshForSelectedFile(autoStart = false, trigger = trigger) || refreshed
            }

            is AuthordMarkdownEditorWithPreview -> {
                editor.refreshPreviewAfterRuntimeStart(trigger) || refreshed
            }

            else -> refreshed
        }
    }
    return refreshed
}
