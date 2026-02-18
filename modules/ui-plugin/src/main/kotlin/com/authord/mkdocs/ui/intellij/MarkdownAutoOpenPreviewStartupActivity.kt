package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val AUTO_OPEN_DEBOUNCE_MS: Long = 180L

/**
 * Auto-opens Authord preview on markdown selection with debounce and in-flight guards.
 */
class MarkdownAutoOpenPreviewStartupActivity(
    private val autoOpenEnabledProvider: (Project) -> Boolean = ::isAutoOpenEnabled,
    private val canStartPreviewProvider: (Project) -> Boolean = { project ->
        PluginCompositionRoot().runtimeIntegration(project).canStartPreview()
    },
    private val previewStarter: (Project) -> Boolean = { project ->
        StartMkdocsAction(resultPresenter = { _, _, _ -> }).invokeForProject(project)
    },
    private val previewNavigator: (Project, String) -> Unit = { project, path ->
        PluginCompositionRoot().runtimeIntegration(project).navigateToSelectedFile(path)
    },
    private val toolWindowOpener: (Project) -> Unit = ::showAuthordToolWindow,
    private val selectionEligiblePredicate: (Project, String) -> Boolean = ::isEligibleMarkdownSelection,
    private val delayedInvoker: (Long, () -> Unit) -> Unit = { delayMillis, task ->
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
) : StartupActivity.DumbAware {
    private val selectionGenerationByProject = ConcurrentHashMap<String, AtomicInteger>()
    private val startupInFlightByProject = ConcurrentHashMap<String, AtomicBoolean>()

    override fun runActivity(project: Project) {
        project.messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) {
                    onMarkdownFileSelected(project, file.path)
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    onMarkdownFileSelected(project, event.newFile?.path)
                }
            },
        )

        val selectedPath = runCatching {
            FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path
        }.getOrNull()
        onMarkdownFileSelected(project, selectedPath)
    }

    internal fun onMarkdownFileSelected(project: Project, selectedPath: String?) {
        val path = selectedPath ?: return
        if (!autoOpenEnabledProvider(project)) {
            return
        }
        if (!selectionEligiblePredicate(project, path)) {
            return
        }
        if (!canStartPreviewProvider(project)) {
            return
        }

        val generationCounter = selectionGenerationByProject.computeIfAbsent(project.locationHash) { AtomicInteger(0) }
        val generation = generationCounter.incrementAndGet()
        delayedInvoker(AUTO_OPEN_DEBOUNCE_MS) {
            if (project.isDisposed || generationCounter.get() != generation) {
                return@delayedInvoker
            }
            triggerPreviewStartup(project, path)
        }
    }

    private fun triggerPreviewStartup(project: Project, selectedPath: String) {
        val inFlight = startupInFlightByProject.computeIfAbsent(project.locationHash) { AtomicBoolean(false) }
        if (!inFlight.compareAndSet(false, true)) {
            return
        }

        try {
            if (!autoOpenEnabledProvider(project) || !canStartPreviewProvider(project)) {
                return
            }
            if (!previewStarter(project)) {
                return
            }
            previewNavigator(project, selectedPath)
            toolWindowOpener(project)
        } finally {
            inFlight.set(false)
        }
    }

    companion object {
        internal fun isAutoOpenEnabled(project: Project): Boolean {
            return project.getService(AuthordPreviewSettingsService::class.java)?.autoOpenPreviewOnMarkdownOpen ?: true
        }

        internal fun isEligibleMarkdownSelection(project: Project, selectedPath: String): Boolean {
            if (!isMarkdownPath(selectedPath)) {
                return false
            }
            val projectBasePath = project.basePath ?: return false
            if (!hasMkdocsConfig(projectBasePath)) {
                return false
            }
            return isUnderProject(projectBasePath, selectedPath)
        }

        internal fun isMarkdownPath(path: String): Boolean {
            val normalized = path.lowercase()
            return normalized.endsWith(".md") || normalized.endsWith(".markdown")
        }

        internal fun hasMkdocsConfig(projectBasePath: String): Boolean {
            val base = runCatching { Path.of(projectBasePath) }.getOrNull() ?: return false
            return Files.exists(base.resolve("mkdocs.yml")) || Files.exists(base.resolve("mkdocs.yaml"))
        }

        internal fun isUnderProject(projectBasePath: String, selectedPath: String): Boolean {
            val projectPath = runCatching { Path.of(projectBasePath).toAbsolutePath().normalize() }.getOrNull() ?: return false
            val candidatePath = runCatching { Path.of(selectedPath).toAbsolutePath().normalize() }.getOrNull() ?: return false
            return candidatePath.startsWith(projectPath)
        }
    }
}
