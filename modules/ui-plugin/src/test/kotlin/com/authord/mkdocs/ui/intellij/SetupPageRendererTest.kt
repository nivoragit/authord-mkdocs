package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertTrue

class SetupPageRendererTest {
    @Test
    fun `render contains setup empty state actions and bridge injection`() {
        val renderer = SetupPageRenderer()

        val html = renderer.render("window.__bridge(payload);")

        assertTrue(html.contains("No documentation added"))
        assertTrue(html.contains("add-documentation-button"))
        assertTrue(html.contains("getting-started-link"))
        assertTrue(html.contains("window.prompt"))
        assertTrue(html.contains("Project name is required"))
        assertTrue(html.contains("window.__bridge(payload);"))
    }
}
