package com.authord.mkdocs.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewPaneCoordinatorTest {
    @Test
    fun `opens side-by-side preview and tracks active url`() {
        val coordinator = PreviewPaneCoordinator()

        coordinator.open("project-1", "http://127.0.0.1:8000/")
        coordinator.navigate("project-1", "/guide/")

        val state = coordinator.currentState("project-1")
        assertNotNull(state)
        assertEquals("/guide/", state.currentRoute)
        assertEquals("http://127.0.0.1:8000/guide/", coordinator.currentUrl("project-1"))
    }

    @Test
    fun `returns null for missing session and keeps root url stable`() {
        val coordinator = PreviewPaneCoordinator()

        assertNull(coordinator.currentState("missing"))
        assertNull(coordinator.navigate("missing", "/foo/"))

        coordinator.open("project-1", "http://127.0.0.1:8000/")
        assertEquals("http://127.0.0.1:8000/", coordinator.currentUrl("project-1"))
    }

    @Test
    fun `normalizes route without leading or trailing slash`() {
        val coordinator = PreviewPaneCoordinator()
        coordinator.open("project-1", "http://127.0.0.1:8000/")

        coordinator.navigate("project-1", "guide")

        assertEquals("http://127.0.0.1:8000/guide/", coordinator.currentUrl("project-1"))
    }

    @Test
    fun `preserves html route without appending trailing slash`() {
        val coordinator = PreviewPaneCoordinator()
        coordinator.open("project-1", "http://127.0.0.1:8000/")

        coordinator.navigate("project-1", "/guide/setup.html")

        assertEquals("http://127.0.0.1:8000/guide/setup.html", coordinator.currentUrl("project-1"))
    }

    @Test
    fun `handles edge cases in url normalization`() {
        val coordinator = PreviewPaneCoordinator()

        // Test empty base url normalization
        coordinator.open("empty-base", "   ")
        assertEquals("/", coordinator.currentState("empty-base")?.baseUrl)

        // Test base url without trailing slash
        coordinator.open("no-slash", "http://localhost:8000")
        assertEquals("http://localhost:8000/", coordinator.currentState("no-slash")?.baseUrl)

        // Test navigation to root route normalization
        coordinator.navigate("no-slash", "/")
        assertEquals("/", coordinator.currentState("no-slash")?.currentRoute)
    }
}
