package integration.navigation

import com.authord.mkdocs.core.navigation.RouteMappingService
import com.authord.mkdocs.ui.NavigationCoordinator
import com.authord.mkdocs.ui.PreviewNavigationFailureHandler
import com.authord.mkdocs.ui.PreviewPaneCoordinator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExplorerSelectionToPreviewIT {
    @Test
    fun `file selection updates preview route`() {
        val preview = PreviewPaneCoordinator()
        preview.open("project-1", "http://127.0.0.1:8000/")

        val coordinator = NavigationCoordinator(
            routeMappingService = RouteMappingService(),
            previewPaneCoordinator = preview,
            failureHandler = PreviewNavigationFailureHandler(),
        )

        val result = coordinator.onFileSelected("project-1", "docs/foo.md")

        assertTrue(result.applied)
        assertEquals("http://127.0.0.1:8000/foo/", preview.currentUrl("project-1"))
    }
}
