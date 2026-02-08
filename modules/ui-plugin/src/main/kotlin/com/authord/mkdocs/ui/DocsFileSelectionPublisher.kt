package com.authord.mkdocs.ui

data class DocsFileSelectedEvent(
    val projectId: String,
    val selectedPath: String,
)

class DocsFileSelectionPublisher {
    private val listeners = mutableListOf<(DocsFileSelectedEvent) -> Unit>()

    fun subscribe(listener: (DocsFileSelectedEvent) -> Unit) {
        listeners += listener
    }

    fun publish(event: DocsFileSelectedEvent) {
        listeners.forEach { it(event) }
    }
}
