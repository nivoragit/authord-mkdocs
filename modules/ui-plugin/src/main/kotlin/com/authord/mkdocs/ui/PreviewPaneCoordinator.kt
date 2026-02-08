package com.authord.mkdocs.ui

/**
 * Preview session state per project.
 */
data class PreviewPaneState(
    val projectId: String,
    val baseUrl: String,
    val currentRoute: String,
    val isOpen: Boolean,
)

/**
 * Tracks and updates preview pane routing state for each project.
 */
class PreviewPaneCoordinator {
    private val sessions = mutableMapOf<String, PreviewPaneState>()

    /** Opens preview state for a project with a detected base URL. */
    fun open(projectId: String, baseUrl: String): PreviewPaneState {
        val state = PreviewPaneState(
            projectId = projectId,
            baseUrl = baseUrl,
            currentRoute = "/",
            isOpen = true,
        )
        sessions[projectId] = state
        return state
    }

    /** Navigates existing preview session to a route. */
    fun navigate(projectId: String, route: String): PreviewPaneState? {
        val existing = sessions[projectId] ?: return null
        val normalized = normalizeRoute(route)
        val updated = existing.copy(currentRoute = normalized)
        sessions[projectId] = updated
        return updated
    }

    /** Returns currently resolved absolute preview URL for a project. */
    fun currentUrl(projectId: String): String? {
        val state = sessions[projectId] ?: return null
        return if (state.currentRoute == "/") state.baseUrl else "${state.baseUrl.trimEnd('/')}${state.currentRoute}"
    }

    /** Returns current session state for a project. */
    fun currentState(projectId: String): PreviewPaneState? = sessions[projectId]

    private fun normalizeRoute(route: String): String {
        if (route == "/") return route
        val withLeading = if (route.startsWith("/")) route else "/$route"
        return if (withLeading.endsWith("/")) withLeading else "$withLeading/"
    }
}
