package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MkDocsPreviewBrowserServiceTest {
    @Test
    fun `ensurePreviewContent returns a single instance per project service`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-service-single")
        val service = MkDocsPreviewBrowserService(project)

        val first = service.ensurePreviewContent()
        val second = service.ensurePreviewContent()

        assertSame(first, second)
        assertTrue(service.hasInitializedPreviewContent())
        assertFalse(service.markdownPreviewActivated())
    }

    @Test
    fun `markMarkdownActivated toggles activation and initializes preview surface`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-service-activated")
        val service = MkDocsPreviewBrowserService(project)

        val preview = service.markMarkdownActivated()

        assertSame(preview, service.previewContentOrNull())
        assertTrue(service.markdownPreviewActivated())
    }
}
