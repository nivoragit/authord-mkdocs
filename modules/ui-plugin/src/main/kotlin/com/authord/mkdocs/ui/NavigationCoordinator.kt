package com.authord.mkdocs.ui

import com.authord.mkdocs.core.navigation.RouteMappingService

/**
 * Result returned by docs-selection navigation handling.
 */
data class NavigationResult(
    val applied: Boolean,
    val route: String = "",
    val message: String = "",
)

/**
 * Coordinates docs file selection to preview route updates.
 */
class NavigationCoordinator(
    private val routeMappingService: RouteMappingService,
    private val previewPaneCoordinator: PreviewPaneCoordinator,
    private val failureHandler: PreviewNavigationFailureHandler,
) {
    /**
     * Applies route navigation for a selected file path.
     */
    fun onFileSelected(projectId: String, selectedPath: String): NavigationResult {
        val route = routeMappingService.mapToRoute(selectedPath)
            ?: return NavigationResult(
                applied = false,
                message = failureHandler.messageFor(selectedPath),
            )

        previewPaneCoordinator.navigate(projectId, route)
        return NavigationResult(applied = true, route = route)
    }
}
