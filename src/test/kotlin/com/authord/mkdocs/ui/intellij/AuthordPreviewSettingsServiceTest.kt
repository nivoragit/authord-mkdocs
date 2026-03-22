package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthordPreviewSettingsServiceTest {
    @Test
    fun `auto-open setting defaults to true`() {
        val service = AuthordPreviewSettingsService()

        assertTrue(service.autoOpenPreviewOnMarkdownOpen)
    }

    @Test
    fun `auto-open setting can be disabled through state load`() {
        val service = AuthordPreviewSettingsService()
        service.loadState(AuthordPreviewSettingsState(autoOpenPreviewOnMarkdownOpen = false))

        assertFalse(service.autoOpenPreviewOnMarkdownOpen)
    }
}
