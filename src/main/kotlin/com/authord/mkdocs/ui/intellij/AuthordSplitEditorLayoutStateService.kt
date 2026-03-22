package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.TextEditorWithPreview

data class AuthordSplitEditorLayoutState(
    var preferredLayoutName: String = TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW.name,
)

@Service(Service.Level.PROJECT)
@State(
    name = "AuthordSplitEditorLayoutState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class AuthordSplitEditorLayoutStateService : PersistentStateComponent<AuthordSplitEditorLayoutState> {
    private var state: AuthordSplitEditorLayoutState = AuthordSplitEditorLayoutState()

    fun preferredLayout(): TextEditorWithPreview.Layout {
        return parseAuthordSplitLayoutName(state.preferredLayoutName)
            ?: TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
    }

    fun setPreferredLayout(layout: TextEditorWithPreview.Layout) {
        state.preferredLayoutName = layout.name
    }

    override fun getState(): AuthordSplitEditorLayoutState = state

    override fun loadState(state: AuthordSplitEditorLayoutState) {
        this.state = state
    }
}
