package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewNavigationPolicyTest {
    @Test
    fun `allows local preview urls in main frame`() {
        val localhost = PreviewNavigationPolicy.decide(
            url = "http://localhost:8000/docs/",
            isMainFrame = true,
            userGesture = true,
            isRedirect = false,
        )
        val loopback = PreviewNavigationPolicy.decide(
            url = "http://127.0.0.1:8000/docs/",
            isMainFrame = true,
            userGesture = true,
            isRedirect = false,
        )

        assertTrue(localhost.allowInPreview)
        assertFalse(localhost.openExternally)
        assertTrue(loopback.allowInPreview)
        assertFalse(loopback.openExternally)
    }

    @Test
    fun `blocks non-local main-frame navigation and opens system browser`() {
        val decision = PreviewNavigationPolicy.decide(
            url = "https://example.com/docs/",
            isMainFrame = true,
            userGesture = true,
            isRedirect = false,
        )

        assertFalse(decision.allowInPreview)
        assertTrue(decision.openExternally)
    }

    @Test
    fun `blocks non-local navigation without user gesture and keeps browser in-pane`() {
        val decision = PreviewNavigationPolicy.decide(
            url = "https://example.com/auto-redirect",
            isMainFrame = true,
            userGesture = false,
            isRedirect = false,
        )

        assertFalse(decision.allowInPreview)
        assertFalse(decision.openExternally)
    }

    @Test
    fun `allows non-main-frame navigation to avoid iframe breakage`() {
        val decision = PreviewNavigationPolicy.decide(
            url = "https://example.com/embed",
            isMainFrame = false,
            userGesture = true,
            isRedirect = false,
        )

        assertTrue(decision.allowInPreview)
        assertFalse(decision.openExternally)
    }

    @Test
    fun `allows browser internal schemes`() {
        val aboutDecision = PreviewNavigationPolicy.decide(
            url = "about:blank",
            isMainFrame = true,
            userGesture = false,
            isRedirect = false,
        )
        val fileDecision = PreviewNavigationPolicy.decide(
            url = "file:///tmp/preview.html",
            isMainFrame = true,
            userGesture = false,
            isRedirect = false,
        )

        assertTrue(aboutDecision.allowInPreview)
        assertFalse(aboutDecision.openExternally)
        assertTrue(fileDecision.allowInPreview)
        assertFalse(fileDecision.openExternally)
    }
}
