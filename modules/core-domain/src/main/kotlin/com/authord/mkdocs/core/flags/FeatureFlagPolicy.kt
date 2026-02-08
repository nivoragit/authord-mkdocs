package com.authord.mkdocs.core.flags

/**
 * Feature-flag policy for guarding MVP and future-cycle capabilities.
 */
data class FeatureFlagPolicy(
    val mvpEnabled: Boolean = true,
    val previewSyncEnabled: Boolean = false,
    val aiAssistantEnabled: Boolean = false,
    val vectorIntegrationEnabled: Boolean = false,
    val writersideParityEnabled: Boolean = false,
) {
    /** Returns `true` when MVP activation flow is permitted. */
    fun allowsMvpFlow(): Boolean = mvpEnabled

    /**
     * Returns `true` only when all future-cycle capability flags remain disabled.
     */
    fun disallowsFutureCycleFeatures(): Boolean {
        return !previewSyncEnabled &&
            !aiAssistantEnabled &&
            !vectorIntegrationEnabled &&
            !writersideParityEnabled
    }
}
