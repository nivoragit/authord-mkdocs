package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalLinkCommandTest {
    @Test
    fun `add-external-link inserts external-link node with url`() {
        val aggregate = TopicTreeAggregate("tree-links")

        val result = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-1",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "ext-node",
                title = "External Docs",
                externalUrl = "https://example.com/docs",
                orderIndex = 0,
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        val node = aggregate.snapshot().first { it.nodeId == "ext-node" }
        assertEquals(TopicNodeKind.EXTERNAL_LINK, node.kind)
        assertEquals("https://example.com/docs", node.externalUrl)
        assertEquals(null, node.sourcePath)

        val httpResult = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-http",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "ext-http-node",
                title = "External HTTP Docs",
                externalUrl = "http://example.com/docs",
                orderIndex = 1,
            ),
        )
        assertEquals(TopicTreeCommandStatus.SUCCESS, httpResult.status)
    }

    @Test
    fun `add-external-link rejects malformed urls and missing parent`() {
        val aggregate = TopicTreeAggregate("tree-links")

        val invalidLink = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-invalid",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "invalid-node",
                title = "External Docs",
                externalUrl = "ftp://example.com/docs",
                orderIndex = 0,
            ),
        )

        val missingParent = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-missing-parent",
                treeId = "tree-links",
                parentNodeId = "ghost",
                nodeId = "missing-parent-node",
                title = "External Docs",
                externalUrl = "https://example.com/docs",
                orderIndex = 0,
            ),
        )

        aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-existing",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "dup-node",
                title = "Existing",
                externalUrl = "https://example.com/existing",
                orderIndex = 1,
            ),
        )
        val duplicate = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-duplicate",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "dup-node",
                title = "Duplicate",
                externalUrl = "https://example.com/duplicate",
                orderIndex = 2,
            ),
        )

        val blankTitle = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-blank-title",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "blank-title-node",
                title = " ",
                externalUrl = "https://example.com/blank-title",
                orderIndex = 0,
            ),
        )

        val negativeOrder = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-negative-order",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "negative-order-node",
                title = "Negative Order",
                externalUrl = "https://example.com/negative-order",
                orderIndex = -1,
            ),
        )

        val blankUrl = aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "ext-blank-url",
                treeId = "tree-links",
                parentNodeId = "root",
                nodeId = "blank-url-node",
                title = "Blank URL",
                externalUrl = " ",
                orderIndex = 0,
            ),
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, invalidLink.status)
        assertTrue(invalidLink.violations.any { it.code == "INVALID_LINK" })

        assertEquals(TopicTreeCommandStatus.REJECTED, missingParent.status)
        assertTrue(missingParent.violations.any { it.code == "PARENT_MISSING" })
        assertEquals(TopicTreeCommandStatus.REJECTED, duplicate.status)
        assertTrue(duplicate.violations.any { it.code == "DUPLICATE_NODE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, blankTitle.status)
        assertTrue(blankTitle.violations.any { it.code == "INVALID_INPUT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, negativeOrder.status)
        assertTrue(negativeOrder.violations.any { it.code == "INVALID_INPUT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, blankUrl.status)
        assertTrue(blankUrl.violations.any { it.code == "INVALID_INPUT" })
    }
}
