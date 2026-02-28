package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReloadCoalescingGateTest {
    @Test
    fun `first reload is never suppressed and subsequent reload inside window is suppressed`() {
        var nowMillis = 1_000L
        val gate = ReloadCoalescingGate(
            coalescingWindowMs = 300L,
            nowMillisProvider = { nowMillis },
        )

        assertFalse(gate.shouldSuppressReload())

        gate.recordReload()
        nowMillis += 150L

        assertTrue(gate.shouldSuppressReload())
    }

    @Test
    fun `reload is allowed once coalescing window elapses`() {
        var nowMillis = 5_000L
        val gate = ReloadCoalescingGate(
            coalescingWindowMs = 300L,
            nowMillisProvider = { nowMillis },
        )

        gate.recordReload()
        nowMillis += 300L

        assertFalse(gate.shouldSuppressReload())
    }

    @Test
    fun `non-positive coalescing window disables suppression`() {
        var nowMillis = 10_000L
        val gate = ReloadCoalescingGate(
            coalescingWindowMs = 0L,
            nowMillisProvider = { nowMillis },
        )

        gate.recordReload()
        nowMillis += 1L

        assertFalse(gate.shouldSuppressReload())
    }
}
