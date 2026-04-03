package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile

/**
 * Initializes persistent preview resources only after an eligible docs markdown file is opened.
 */
internal class AuthordMarkdownOpenPreviewListener(
    private val runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val routerResolver: () -> PreviewRouterService = { PreviewRouterService() },
    private val browserServiceResolver: (Project) -> MkDocsPreviewBrowserService? = { project ->
        runCatching { project.getService(MkDocsPreviewBrowserService::class.java) }.getOrNull()
    },
    private val settingsServiceResolver: (Project) -> AuthordPreviewSettingsService? = { project ->
        runCatching { project.getService(AuthordPreviewSettingsService::class.java) }.getOrNull()
    },
    private val startupFailureHandler: PreviewStartupFailureHandler = PreviewStartupFailureHandler(),
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
        if (isPreviewDispatchSuppressed(project, selectedPath)) {
            return
        }
        val decision = routerResolver().decide(project, selectedPath)
        if (decision.target != PreviewRouteTarget.AUTHORD) {
            return
        }
        val runtimeService = runtimeServiceResolver(project)

        val settings = settingsServiceResolver(project)
        if (settings?.autoOpenPreviewOnMarkdownOpen == false) {
            return
        }

        val browserService = browserServiceResolver(project) ?: return
        browserService.markMarkdownActivated()

        if (runtimeService.isRuntimeRunning()) {
            runtimeService.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = selectedPath,
                source = PreviewRouteIntentSource.MARKDOWN_OPEN,
                forceReload = false,
                loadUrl = { url, forceReload -> browserService.loadUrl(url, forceReload) },
                shouldRetry = { isFileSelectedForPreview(project, selectedPath) },
            )
            return
        }

        runtimeService.startPreviewAsync(PreviewStartTrigger.ACTION) { result ->
            if (project.isDisposed) {
                return@startPreviewAsync
            }
            if (!result.success) {
                startupFailureHandler.handleFailure(
                    project = project,
                    result = result,
                    selectedPath = selectedPath,
                    onRetry = { onMarkdownOpened(project, selectedPath) },
                )
                return@startPreviewAsync
            }
            val applied = runtimeService.dispatchPreviewForSelectedFileWithRetry(
                selectedPath = selectedPath,
                source = PreviewRouteIntentSource.MARKDOWN_OPEN,
                forceReload = false,
                loadUrl = { url, forceReload -> browserService.loadUrl(url, forceReload) },
                shouldRetry = { isFileSelectedForPreview(project, selectedPath) },
            )
            if (!applied) {
                val fallbackUrl = result.previewUrl.ifBlank { runtimeService.currentPreviewUrl().orEmpty() }
                if (fallbackUrl.isNotBlank()) {
                    browserService.loadUrl(fallbackUrl, forceReload = true)
                }
            }
        }
    }

    private fun isFileSelectedForPreview(project: Project, selectedPath: String): Boolean {
        if (project.isDisposed) {
            return false
        }
        val selectedFiles = runCatching { FileEditorManager.getInstance(project).selectedFiles.toList() }.getOrNull()
            ?: return true
        val expected = comparablePath(selectedPath)
        return selectedFiles.any { file ->
            comparablePath(file.path) == expected
        }
    }

    private fun comparablePath(path: String): String {
        val normalized = path.replace('\\', '/')
        return if (SystemInfoRt.isFileSystemCaseSensitive) normalized else normalized.lowercase()
    }

    private fun isPreviewDispatchSuppressed(project: Project, selectedPath: String): Boolean {
        val suppression = project.getUserData(SUPPRESSED_SELECTION_KEY) ?: return false
        val now = System.currentTimeMillis()
        if (now > suppression.expiresAtMillis) {
            project.putUserData(SUPPRESSED_SELECTION_KEY, null)
            return false
        }
        return suppression.comparablePath == comparablePath(selectedPath)
    }

    private data class PreviewSelectionSuppression(
        val comparablePath: String,
        val expiresAtMillis: Long,
    )

    companion object {
        private val SUPPRESSED_SELECTION_KEY: Key<PreviewSelectionSuppression> =
            Key.create("authord.preview.suppressed.selection")
        private const val SUPPRESSION_WINDOW_MILLIS: Long = 1_500L

        internal fun suppressNextSelectionDrivenDispatch(project: Project, filePath: String) {
            val normalized = filePath.replace('\\', '/')
            val comparable = if (SystemInfoRt.isFileSystemCaseSensitive) normalized else normalized.lowercase()
            val expiresAt = System.currentTimeMillis() + SUPPRESSION_WINDOW_MILLIS
            project.putUserData(
                SUPPRESSED_SELECTION_KEY,
                PreviewSelectionSuppression(comparablePath = comparable, expiresAtMillis = expiresAt),
            )
        }
    }
}
