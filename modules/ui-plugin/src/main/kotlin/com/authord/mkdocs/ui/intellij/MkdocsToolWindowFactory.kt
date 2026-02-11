package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
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
import java.awt.BorderLayout
import java.awt.Point
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane

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
    private val scrollFlushDelayMs: Long = 20L
    private val typingGenerationByProject = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingScrollProgressByProject = ConcurrentHashMap<String, Double>()
    private val lastScrollProgressByProject = ConcurrentHashMap<String, Double>()
    private val scrollFlushScheduledByProject = ConcurrentHashMap<String, AtomicBoolean>()
    /**
     * Registers minimal content inside the tool window manager.
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val runtimeService = runtimeServiceResolver(project)
        val previewContent = previewContentFactory()
        val panel = createShellContentPanel(project, runtimeService, previewContent)
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
        registerDocumentTypingSync(project, runtimeService, previewContent)
        registerEditorScrollSync(project, runtimeService, previewContent)
    }

    /**
     * Keeps the tool window available for all open projects.
     */
    override fun shouldBeAvailable(project: Project): Boolean = true

    internal fun createShellContentPanel(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService = runtimeServiceResolver(project),
        previewContent: PreviewContent = previewContentFactory(),
    ): JPanel {
        val panel = JPanel(BorderLayout())

        panel.add(JLabel("MkDocs Preview Shell"), BorderLayout.NORTH)
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
                    scheduleScrollSync(
                        project = project,
                        runtimeService = runtimeService,
                        previewContent = previewContent,
                        selectedPath = selectedPath,
                        rawDelta = rawDelta,
                        documentLength = event.editor.document.textLength,
                        visibleStartOffset = visibleOffsets.startOffset,
                        visibleEndOffset = visibleOffsets.endOffset,
                    )
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
        rawDelta: Int,
        documentLength: Int,
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

        val normalizedDocumentLength = documentLength.coerceAtLeast(1)
        val rawProgress = calculateVisibleProgress(
            visibleStartOffset = visibleStartOffset,
            visibleEndOffset = visibleEndOffset,
            documentLength = normalizedDocumentLength,
        )
        val projectId = project.locationHash
        val previousProgress = lastScrollProgressByProject[projectId]
        val targetProgress = rawProgress

        if (previousProgress != null && kotlin.math.abs(targetProgress - previousProgress) < 0.001) {
            return false
        }

        lastScrollProgressByProject[projectId] = targetProgress
        pendingScrollProgressByProject[projectId] = targetProgress

        val scheduled = scrollFlushScheduledByProject.computeIfAbsent(projectId) { AtomicBoolean(false) }
        if (!scheduled.compareAndSet(false, true)) {
            return true
        }

        delayedInvoker(scrollFlushDelayMs) {
            if (project.isDisposed) {
                scheduled.set(false)
                return@delayedInvoker
            }

            val progressToApply = pendingScrollProgressByProject[projectId] ?: run {
                scheduled.set(false)
                return@delayedInvoker
            }
            scheduled.set(false)
            previewContent.scrollToProgress(progressToApply)
        }

        return true
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

    private fun calculateVisibleProgress(
        visibleStartOffset: Int,
        visibleEndOffset: Int,
        documentLength: Int,
    ): Double {
        val start = visibleStartOffset.coerceAtLeast(0)
        val windowSize = (visibleEndOffset - visibleStartOffset).coerceAtLeast(1)
        val maxScrollableStart = (documentLength - windowSize).coerceAtLeast(1)
        return (start.toDouble() / maxScrollableStart.toDouble()).coerceIn(0.0, 1.0)
    }
}

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
}

private class JcefPreviewContent : PreviewContent {
    private val browser = JBCefBrowser()

    override val component: JComponent = browser.component

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
                state.target = target;

                if (!state.raf) {
                    const step = function() {
                        const current = root.scrollTop;
                        const delta = state.target - current;
                        if (Math.abs(delta) < 1) {
                            root.scrollTop = state.target;
                            state.raf = 0;
                            window.__authordPreviewSyncState = state;
                            return;
                        }
                        root.scrollTop = current + (delta * 0.35);
                        state.raf = window.requestAnimationFrame(step);
                        window.__authordPreviewSyncState = state;
                    };
                    state.raf = window.requestAnimationFrame(step);
                }

                window.__authordPreviewSyncState = state;
            })();
        """.trimIndent()
        browser.cefBrowser.executeJavaScript(script, "about:blank", 0)
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
