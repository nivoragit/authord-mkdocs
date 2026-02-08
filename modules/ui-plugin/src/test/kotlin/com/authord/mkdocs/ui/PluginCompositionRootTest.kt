package com.authord.mkdocs.ui

import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginCompositionRootTest {
    @Test
    fun `wires command bus and default adapters`() {
        val root = PluginCompositionRoot()
        val wiring = root.create(TopicTreeMutationService())

        val result = wiring.commandBus.dispatch(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Title",
                orderIndex = 0,
            )
        )

        assertNotNull(wiring.commandRegistry)
        assertNotNull(wiring.previewSyncPort)
        assertNotNull(wiring.vectorStorePort)
        assertTrue(result.message.isNotBlank())
        assertEquals("cmd-1", result.commandId)
    }
}
