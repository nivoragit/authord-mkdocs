package com.authord.mkdocs.ui

class PreviewNavigationFailureHandler {
    fun messageFor(selectedPath: String): String {
        return "Unable to map $selectedPath to a preview route."
    }
}
