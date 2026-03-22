package com.authord.mkdocs.core.flags

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFlagPolicyTest {
    @Test
    fun `default policy allows MVP and disables future-cycle features`() {
        val policy = FeatureFlagPolicy()

        assertTrue(policy.mvpEnabled)
        assertFalse(policy.previewSyncEnabled)
        assertFalse(policy.aiAssistantEnabled)
        assertFalse(policy.vectorIntegrationEnabled)
        assertFalse(policy.writersideParityEnabled)
        assertTrue(policy.allowsMvpFlow())
        assertTrue(policy.disallowsFutureCycleFeatures())
    }

    @Test
    fun `future-cycle flags invalidate policy`() {
        val policy = FeatureFlagPolicy(previewSyncEnabled = true)

        assertFalse(policy.disallowsFutureCycleFeatures())
    }
}
