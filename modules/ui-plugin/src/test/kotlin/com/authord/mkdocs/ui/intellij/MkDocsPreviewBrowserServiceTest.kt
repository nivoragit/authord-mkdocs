package com.authord.mkdocs.ui.intellij

import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MkDocsPreviewBrowserServiceTest {
    private class RecordingPreviewContent : PreviewContent {
        override val component = JPanel()
        val loadedUrls = mutableListOf<String>()

        override fun loadUrl(url: String) {
            loadedUrls += url
        }
    }

    private fun injectPreviewContent(service: MkDocsPreviewBrowserService, preview: PreviewContent) {
        val field = MkDocsPreviewBrowserService::class.java.getDeclaredField("previewContent")
        field.isAccessible = true
        field.set(service, preview)
    }

    private fun recordObservedReload(service: MkDocsPreviewBrowserService) {
        val field = MkDocsPreviewBrowserService::class.java.getDeclaredField("reloadCoalescingGate")
        field.isAccessible = true
        val gate = field.get(service) as? ReloadCoalescingGate
        assertNotNull(gate)
        gate.recordReload()
    }

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

    @Test
    fun `split owner takes preview component ownership and tool window regains it after split deactivation`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-owner-priority")
        val service = MkDocsPreviewBrowserService(project)
        val toolWindowPreview = service.previewContentForOwner(PreviewOwnerKind.TOOL_WINDOW, "tool")
        val splitPreview = service.previewContentForOwner(PreviewOwnerKind.SPLIT_EDITOR, "split")
        val shared = service.ensurePreviewContent()

        service.activatePreviewOwner(PreviewOwnerKind.TOOL_WINDOW, "tool")

        assertSame(toolWindowPreview.component, shared.component.parent)

        service.activatePreviewOwner(PreviewOwnerKind.SPLIT_EDITOR, "split")
        assertSame(splitPreview.component, shared.component.parent)

        service.deactivatePreviewOwner(PreviewOwnerKind.SPLIT_EDITOR, "split")
        assertSame(toolWindowPreview.component, shared.component.parent)
    }

    @Test
    fun `shared browser deduplicates same route across owners but still navigates changed routes`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-route-dedup")
        val service = MkDocsPreviewBrowserService(project)
        val recordingPreview = RecordingPreviewContent()
        injectPreviewContent(service, recordingPreview)

        service.loadUrl("http://127.0.0.1:8000/a/")
        service.loadUrl("http://127.0.0.1:8000/a/")
        service.loadUrl("http://127.0.0.1:8000/b/")
        service.loadUrl("http://127.0.0.1:8000/a/")

        assertEquals(
            listOf(
                "http://127.0.0.1:8000/a/",
                "http://127.0.0.1:8000/b/",
                "http://127.0.0.1:8000/a/",
            ),
            recordingPreview.loadedUrls,
        )
    }

    @Test
    fun `force reload bypasses shared last url dedupe`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-force-reload")
        val service = MkDocsPreviewBrowserService(project)
        val recordingPreview = RecordingPreviewContent()
        injectPreviewContent(service, recordingPreview)

        service.loadUrl("http://127.0.0.1:8000/a/")
        service.loadUrl("http://127.0.0.1:8000/a/", forceReload = true)

        assertEquals(
            listOf(
                "http://127.0.0.1:8000/a/",
                "http://127.0.0.1:8000/a/",
            ),
            recordingPreview.loadedUrls,
        )
    }

    @Test
    fun `resetLastLoadedUrl clears dedupe cache`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-reset-last-loaded")
        val service = MkDocsPreviewBrowserService(project)
        val recordingPreview = RecordingPreviewContent()
        injectPreviewContent(service, recordingPreview)

        service.loadUrl("http://127.0.0.1:8000/a/")
        service.resetLastLoadedUrl()
        service.loadUrl("http://127.0.0.1:8000/a/")

        assertEquals(
            listOf(
                "http://127.0.0.1:8000/a/",
                "http://127.0.0.1:8000/a/",
            ),
            recordingPreview.loadedUrls,
        )
    }

    @Test
    fun `reload gate suppresses duplicate non forced navigation after recent browser reload`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-reload-gate")
        val service = MkDocsPreviewBrowserService(project)
        val recordingPreview = RecordingPreviewContent()
        injectPreviewContent(service, recordingPreview)

        service.loadUrl("http://127.0.0.1:8000/a/")
        recordObservedReload(service)
        service.resetLastLoadedUrl()
        service.loadUrl("http://127.0.0.1:8000/a/")

        assertEquals(
            listOf("http://127.0.0.1:8000/a/"),
            recordingPreview.loadedUrls,
        )
    }

    @Test
    fun `force reload bypasses coalescing gate`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-browser-reload-gate-force")
        val service = MkDocsPreviewBrowserService(project)
        val recordingPreview = RecordingPreviewContent()
        injectPreviewContent(service, recordingPreview)

        service.loadUrl("http://127.0.0.1:8000/a/")
        recordObservedReload(service)
        service.resetLastLoadedUrl()
        service.loadUrl("http://127.0.0.1:8000/a/", forceReload = true)

        assertEquals(
            listOf(
                "http://127.0.0.1:8000/a/",
                "http://127.0.0.1:8000/a/",
            ),
            recordingPreview.loadedUrls,
        )
    }
}
