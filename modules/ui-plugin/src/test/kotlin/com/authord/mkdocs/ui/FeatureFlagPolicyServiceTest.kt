package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureFlagPolicyServiceTest {
    @Test
    fun `current and update expose active policy`() {
        val service = FeatureFlagPolicyService()

        assertTrue(service.current().mvpEnabled)

        service.update(FeatureFlagPolicy(mvpEnabled = false))
        assertEquals(false, service.current().mvpEnabled)
        assertFalse(service.allowsMvpOnlyExecution())
    }
}
