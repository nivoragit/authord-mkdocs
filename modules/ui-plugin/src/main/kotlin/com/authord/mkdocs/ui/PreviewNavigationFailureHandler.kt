package com.authord.mkdocs.ui

/**
 * Produces failure messages for unmapped docs-to-preview navigation requests.
 */
class PreviewNavigationFailureHandler {
    /**
     * Returns user-facing message for an unsupported selected path.
     */
    fun messageFor(selectedPath: String): String {
        return "Unable to map $selectedPath to a preview route."
    }
}
