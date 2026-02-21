package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.atomic.AtomicInteger

private val PREVIEW_ROUTE_REQUEST_GENERATION_KEY: Key<AtomicInteger> =
    Key.create("authord.markdown.open.previewRouteGeneration")

/**
 * Initializes persistent preview resources only after an eligible docs markdown file is opened.
 */
class AuthordMarkdownOpenPreviewListener(
    private val runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val browserServiceResolver: (Project) -> MkDocsPreviewBrowserService? = { project ->
        runCatching { project.getService(MkDocsPreviewBrowserService::class.java) }.getOrNull()
    },
    private val settingsServiceResolver: (Project) -> AuthordPreviewSettingsService? = { project ->
        runCatching { project.getService(AuthordPreviewSettingsService::class.java) }.getOrNull()
    },
) : FileEditorManagerListener, DumbAware {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        onMarkdownOpened(source.project, file.path)
    }

    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        val selectedPath = event.newFile?.path ?: return
        onMarkdownOpened(event.manager.project, selectedPath)
    }

    private fun onMarkdownOpened(project: Project, selectedPath: String) {
        if (project.isDisposed) {
            return
        }
        if (!isDocsMarkdownPath(selectedPath)) {
            return
        }
        if (!isAuthordPreviewEligible(project.basePath, selectedPath)) {
            return
        }

        val settings = settingsServiceResolver(project)
        if (settings?.autoOpenPreviewOnMarkdownOpen == false) {
            return
        }

        val browserService = browserServiceResolver(project) ?: return
        browserService.markMarkdownActivated()

        val runtimeService = runtimeServiceResolver(project)
        if (runtimeService.isRuntimeRunning()) {
            val routedUrl = runtimeService.navigateToSelectedFile(selectedPath)
            val resolvedUrl = routedUrl ?: runtimeService.currentPreviewUrl()
            if (!resolvedUrl.isNullOrBlank()) {
                loadResolvedRoute(
                    project = project,
                    runtimeService = runtimeService,
                    browserService = browserService,
                    resolvedUrl = resolvedUrl,
                )
            }
            return
        }

        runtimeService.startPreviewAsync(PreviewStartTrigger.ACTION) { result ->
            if (project.isDisposed || !result.success) {
                return@startPreviewAsync
            }
            val routedUrl = runtimeService.navigateToSelectedFile(selectedPath)
            val resolvedUrl = routedUrl ?: result.previewUrl.ifBlank { runtimeService.currentPreviewUrl().orEmpty() }
            if (resolvedUrl.isNotBlank()) {
                loadResolvedRoute(
                    project = project,
                    runtimeService = runtimeService,
                    browserService = browserService,
                    resolvedUrl = resolvedUrl,
                )
            }
        }
    }

    private fun loadResolvedRoute(
        project: Project,
        runtimeService: PluginRuntimeIntegrationService,
        browserService: MkDocsPreviewBrowserService,
        resolvedUrl: String,
    ) {
        val requestGeneration = nextRouteLoadGeneration(project)
        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = resolvedUrl,
            isRequestCurrent = { currentRouteLoadGeneration(project) == requestGeneration },
            isRuntimeRunning = runtimeService::isRuntimeRunning,
            loadUrl = { url, _ -> browserService.loadUrl(url) },
        )
    }

    private fun nextRouteLoadGeneration(project: Project): Int {
        val counter = project.getUserData(PREVIEW_ROUTE_REQUEST_GENERATION_KEY)
            ?: AtomicInteger(0).also { project.putUserData(PREVIEW_ROUTE_REQUEST_GENERATION_KEY, it) }
        return counter.incrementAndGet()
    }

    private fun currentRouteLoadGeneration(project: Project): Int {
        return project.getUserData(PREVIEW_ROUTE_REQUEST_GENERATION_KEY)?.get() ?: 0
    }

    private fun isDocsMarkdownPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.endsWith(".md") && normalized.contains("/docs/")
    }
}
