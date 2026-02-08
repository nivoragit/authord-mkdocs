package com.authord.mkdocs.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DocsFileSelectionPublisherTest {
    @Test
    fun `publishes selected file events to subscribers`() {
        val publisher = DocsFileSelectionPublisher()
        val events = mutableListOf<DocsFileSelectedEvent>()

        publisher.subscribe { events += it }
        publisher.publish(DocsFileSelectedEvent("project-1", "docs/index.md"))

        assertEquals(1, events.size)
        assertEquals("project-1", events.first().projectId)
        assertEquals("docs/index.md", events.first().selectedPath)
    }
}
