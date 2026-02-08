package com.authord.mkdocs.core.flags

data class FeatureFlagPolicy(
    val mvpEnabled: Boolean = true,
    val previewSyncEnabled: Boolean = false,
    val aiAssistantEnabled: Boolean = false,
    val vectorIntegrationEnabled: Boolean = false,
    val writersideParityEnabled: Boolean = false,
) {
    fun allowsMvpFlow(): Boolean = mvpEnabled

    fun disallowsFutureCycleFeatures(): Boolean {
        return !previewSyncEnabled &&
            !aiAssistantEnabled &&
            !vectorIntegrationEnabled &&
            !writersideParityEnabled
    }
}
