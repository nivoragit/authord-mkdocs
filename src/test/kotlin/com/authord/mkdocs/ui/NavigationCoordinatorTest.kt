package com.authord.mkdocs.ui

import com.authord.mkdocs.core.navigation.RouteMappingService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationCoordinatorTest {
    @Test
    fun `applies mapped route to preview`() {
        val preview = PreviewPaneCoordinator()
        preview.open("project-1", "http://127.0.0.1:8000/")

        val coordinator = NavigationCoordinator(
            routeMappingService = RouteMappingService(),
            previewPaneCoordinator = preview,
            failureHandler = PreviewNavigationFailureHandler(),
        )

        val result = coordinator.onFileSelected("project-1", "docs/foo/index.md")

        assertTrue(result.applied)
        assertEquals("/foo/", result.route)
        assertEquals("http://127.0.0.1:8000/foo/", preview.currentUrl("project-1"))
    }

    @Test
    fun `returns failure when mapping cannot be resolved`() {
        val preview = PreviewPaneCoordinator()
        preview.open("project-1", "http://127.0.0.1:8000/")

        val coordinator = NavigationCoordinator(
            routeMappingService = RouteMappingService(),
            previewPaneCoordinator = preview,
            failureHandler = PreviewNavigationFailureHandler(),
        )

        val result = coordinator.onFileSelected("project-1", "notes/todo.txt")

        assertFalse(result.applied)
        assertTrue(result.message.contains("Unable to map"))
    }
}
