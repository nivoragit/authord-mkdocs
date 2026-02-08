package com.authord.mkdocs.ui

data class PreviewPaneState(
    val projectId: String,
    val baseUrl: String,
    val currentRoute: String,
    val isOpen: Boolean,
)

class PreviewPaneCoordinator {
    private val sessions = mutableMapOf<String, PreviewPaneState>()

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

    fun navigate(projectId: String, route: String): PreviewPaneState? {
        val existing = sessions[projectId] ?: return null
        val normalized = normalizeRoute(route)
        val updated = existing.copy(currentRoute = normalized)
        sessions[projectId] = updated
        return updated
    }

    fun currentUrl(projectId: String): String? {
        val state = sessions[projectId] ?: return null
        return if (state.currentRoute == "/") state.baseUrl else "${state.baseUrl.trimEnd('/')}${state.currentRoute}"
    }

    fun currentState(projectId: String): PreviewPaneState? = sessions[projectId]

    private fun normalizeRoute(route: String): String {
        if (route == "/") return route
        val withLeading = if (route.startsWith("/")) route else "/$route"
        return if (withLeading.endsWith("/")) withLeading else "$withLeading/"
    }
}
