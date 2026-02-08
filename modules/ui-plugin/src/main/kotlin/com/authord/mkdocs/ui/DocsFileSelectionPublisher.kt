package com.authord.mkdocs.ui

/**
 * Event emitted when a docs file is selected.
 */
data class DocsFileSelectedEvent(
    val projectId: String,
    val selectedPath: String,
)

/**
 * In-memory publisher for docs selection events.
 */
class DocsFileSelectionPublisher {
    private val listeners = mutableListOf<(DocsFileSelectedEvent) -> Unit>()

    /** Subscribes one listener callback. */
    fun subscribe(listener: (DocsFileSelectedEvent) -> Unit) {
        listeners += listener
    }

    /** Publishes one selection event to registered listeners. */
    fun publish(event: DocsFileSelectedEvent) {
        listeners.forEach { it(event) }
    }
}
