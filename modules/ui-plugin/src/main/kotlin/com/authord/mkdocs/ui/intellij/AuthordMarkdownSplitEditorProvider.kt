package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

private const val AUTHORD_MKDOCS_PREVIEW_EDITOR_TYPE_ID = "authord-mkdocs-preview-editor"

/**
 * Replaces the default Markdown split preview with an Authord-backed MkDocs preview.
 */
class AuthordMarkdownSplitEditorProvider(
    previewProvider: FileEditorProvider = AuthordMarkdownPreviewFileEditorProvider(),
) : TextEditorWithPreviewProvider(previewProvider), DumbAware {
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
        val projectBasePath = project.basePath ?: return false
        val filePath = file.path
        return MarkdownAutoOpenPreviewStartupActivity.isMarkdownPath(filePath) &&
            MarkdownAutoOpenPreviewStartupActivity.hasMkdocsConfig(projectBasePath) &&
            MarkdownAutoOpenPreviewStartupActivity.isUnderProject(projectBasePath, filePath)
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return AuthordMarkdownPreviewFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = AUTHORD_MKDOCS_PREVIEW_EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class AuthordMarkdownEditorWithPreview(
    textEditor: TextEditor,
    private val previewEditor: AuthordMarkdownPreviewFileEditor,
) : TextEditorWithPreview(
    textEditor,
    previewEditor,
    "Authord Preview",
    Layout.SHOW_EDITOR_AND_PREVIEW,
) {
    init {
        previewEditor.refreshForSelectedFile(autoStart = true, trigger = PreviewStartTrigger.ACTION)
    }

    override fun createRightToolbarActionGroup(): ActionGroup {
        return DefaultActionGroup(
            object : DumbAwareAction(
                "Restart Authord Preview",
                "Restart MkDocs runtime and refresh Authord preview",
                AllIcons.Actions.Refresh,
            ) {
                override fun actionPerformed(event: AnActionEvent) {
                    previewEditor.restartPreviewAndRefresh()
                }
            },
            object : DumbAwareAction(
                "Open Authord Treeview",
                "Show the Authord Treeview tool window",
                AllIcons.Nodes.Folder,
            ) {
                override fun actionPerformed(event: AnActionEvent) {
                    val project = event.project ?: textEditor.editor.project ?: return
                    showAuthordToolWindow(project)
                }
            },
        )
    }
}

private class AuthordMarkdownPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile,
    runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val resultPresenter: (Project, String, Boolean) -> Unit = ::presentPreviewResult,
    previewContentFactory: () -> PreviewContent = ::createDefaultPreviewContent,
) : UserDataHolderBase(), FileEditor {
    private val runtimeService = runtimeServiceResolver(project)
    private val previewContent = previewContentFactory()
    private var lastLoadedUrl: String? = null

    init {
        runtimeService.currentPreviewUrl()?.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
    }

    override fun getComponent(): JComponent = previewContent.component

    override fun getPreferredFocusedComponent(): JComponent = previewContent.component

    override fun getName(): String = "Authord Preview"

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = !project.isDisposed && file.isValid

    override fun selectNotify() {
        refreshForSelectedFile(autoStart = true, trigger = PreviewStartTrigger.ACTION)
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun getFile(): VirtualFile = file

    override fun dispose() = Unit

    internal fun restartPreviewAndRefresh() {
        val restartResult = runtimeService.restartPreview(PreviewStartTrigger.ACTION)
        resultPresenter(project, formatPreviewResultMessage(restartResult), restartResult.success)
        if (!restartResult.success) {
            return
        }
        refreshForSelectedFile(autoStart = false, trigger = PreviewStartTrigger.ACTION)
    }

    internal fun refreshForSelectedFile(autoStart: Boolean, trigger: PreviewStartTrigger): Boolean {
        if (project.isDisposed || !file.isValid) {
            return false
        }

        if (autoStart && !runtimeService.isRuntimeRunning()) {
            val startResult = runtimeService.startPreview(trigger)
            if (!startResult.success) {
                resultPresenter(project, formatPreviewResultMessage(startResult), false)
                return false
            }
            startResult.previewUrl.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
        }

        val routedUrl = runtimeService.navigateToSelectedFile(file.path)
        val resolvedUrl = routedUrl ?: runtimeService.currentPreviewUrl()
        resolvedUrl?.takeIf { it.isNotBlank() }?.let(::loadUrlIfChanged)
        return resolvedUrl != null
    }

    private fun loadUrlIfChanged(url: String) {
        if (lastLoadedUrl == url) {
            return
        }
        lastLoadedUrl = url
        previewContent.loadUrl(url)
    }
}
