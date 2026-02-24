package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

data class AuthordPreviewSettingsState(
    var autoOpenPreviewOnMarkdownOpen: Boolean = true,
    var strictPreflightOnDependencyChange: Boolean = false,
)

@Service(Service.Level.PROJECT)
@State(
    name = "AuthordPreviewSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class AuthordPreviewSettingsService : PersistentStateComponent<AuthordPreviewSettingsState> {
    private var state: AuthordPreviewSettingsState = AuthordPreviewSettingsState()

    var autoOpenPreviewOnMarkdownOpen: Boolean
        get() = state.autoOpenPreviewOnMarkdownOpen
        set(value) {
            state.autoOpenPreviewOnMarkdownOpen = value
        }

    var strictPreflightOnDependencyChange: Boolean
        get() = state.strictPreflightOnDependencyChange
        set(value) {
            state.strictPreflightOnDependencyChange = value
        }

    override fun getState(): AuthordPreviewSettingsState = state

    override fun loadState(state: AuthordPreviewSettingsState) {
        this.state = state
    }
}
