package com.authord.mkdocs.ui.intellij

import java.util.concurrent.atomic.AtomicLong

internal class ReloadCoalescingGate(
    private val coalescingWindowMs: Long = DEFAULT_COALESCING_WINDOW_MS,
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private val sanitizedWindowMs = coalescingWindowMs.coerceAtLeast(0L)
    private val lastReloadTimestampMs = AtomicLong(UNSET_TIMESTAMP_MS)

    fun shouldSuppressReload(): Boolean {
        if (sanitizedWindowMs <= 0L) {
            return false
        }
        val last = lastReloadTimestampMs.get()
        if (last == UNSET_TIMESTAMP_MS) {
            return false
        }
        val now = nowMillisProvider()
        val delta = now - last
        return delta in 0 until sanitizedWindowMs
    }

    fun recordReload() {
        lastReloadTimestampMs.set(nowMillisProvider())
    }

    private companion object {
        private const val DEFAULT_COALESCING_WINDOW_MS: Long = 300L
        private const val UNSET_TIMESTAMP_MS: Long = Long.MIN_VALUE
    }
}
