package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutOfScopeGuardrailsTest {
    @Test
    fun `default policy enforces mvp-only behavior`() {
        val service = FeatureFlagPolicyService(FeatureFlagPolicy())

        assertTrue(service.allowsMvpOnlyExecution())
    }

    @Test
    fun `future-cycle flags disable mvp-only guardrail`() {
        val service = FeatureFlagPolicyService(
            FeatureFlagPolicy(
                previewSyncEnabled = true,
                aiAssistantEnabled = true,
                vectorIntegrationEnabled = true,
                writersideParityEnabled = true,
            )
        )

        assertFalse(service.allowsMvpOnlyExecution())
    }
}
