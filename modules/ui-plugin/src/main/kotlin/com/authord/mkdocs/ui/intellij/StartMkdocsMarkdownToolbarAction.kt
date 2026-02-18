package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

/**
 * Markdown-toolbar entry point that reuses the existing preview start action pipeline.
 */
class StartMkdocsMarkdownToolbarAction(
    private val delegate: StartMkdocsAction = StartMkdocsAction(),
    private val markdownContextPredicate: (AnActionEvent) -> Boolean = ::isMarkdownEditorContext,
    private val toolWindowOpener: (com.intellij.openapi.project.Project) -> Unit = ::showAuthordToolWindow,
) : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = delegate.actionUpdateThread

    override fun update(event: AnActionEvent) {
        val state = delegate.resolvePresentationState(event.project)
        val markdownContext = markdownContextPredicate(event)
        event.presentation.isVisible = state.visible && markdownContext
        event.presentation.isEnabled = state.enabled && markdownContext
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!markdownContextPredicate(event)) {
            return
        }
        if (delegate.invokeForProject(project)) {
            toolWindowOpener(project)
        }
    }

    companion object {
        internal fun isMarkdownEditorContext(event: AnActionEvent): Boolean {
            val extension = event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension?.lowercase() ?: return false
            return extension == "md" || extension == "markdown"
        }
    }
}
