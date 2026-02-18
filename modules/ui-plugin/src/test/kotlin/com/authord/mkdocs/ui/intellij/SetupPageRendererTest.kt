package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertTrue

class SetupPageRendererTest {
    @Test
    fun `render contains setup form fields and bridge injection`() {
        val renderer = SetupPageRenderer()

        val html = renderer.render("window.__bridge(payload);")

        assertTrue(html.contains("Create Documentation"))
        assertTrue(html.contains("project-name-input"))
        assertTrue(html.contains("create-project-button"))
        assertTrue(html.contains("window.__bridge(payload);"))
    }
}

