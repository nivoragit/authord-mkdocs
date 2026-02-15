package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddExistingFileCommandTest {
    @Test
    fun `add-existing-file inserts markdown-backed page node under selected parent`() {
        val aggregate = TopicTreeAggregate("tree-existing")

        val result = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-1",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "guide-node",
                title = "Guide",
                relativePath = "guide/index.md",
                orderIndex = 0,
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        val added = aggregate.snapshot().first { it.nodeId == "guide-node" }
        assertEquals("root", added.parentNodeId)
        assertEquals("Guide", added.title)
        assertEquals(TopicNodeKind.PAGE, added.kind)
        assertEquals("guide/index.md", added.sourcePath)
    }

    @Test
    fun `add-existing-file rejects missing parent, invalid paths, and duplicate node ids`() {
        val aggregate = TopicTreeAggregate("tree-existing")

        val missingParent = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-missing",
                treeId = "tree-existing",
                parentNodeId = "ghost",
                nodeId = "n1",
                title = "Guide",
                relativePath = "guide/index.md",
                orderIndex = 0,
            ),
        )

        aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-ok",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Guide",
                relativePath = "guide/index.md",
                orderIndex = 0,
            ),
        )

        val duplicate = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-duplicate",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Guide Duplicate",
                relativePath = "guide/other.md",
                orderIndex = 1,
            ),
        )

        val nonMarkdown = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-non-md",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n2",
                title = "Asset",
                relativePath = "assets/logo.png",
                orderIndex = 0,
            ),
        )

        val blankTitle = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-blank-title",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n3",
                title = " ",
                relativePath = "guide/blank-title.md",
                orderIndex = 0,
            ),
        )

        val negativeOrder = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-negative-order",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n4",
                title = "Negative",
                relativePath = "guide/negative.md",
                orderIndex = -1,
            ),
        )

        val blankPath = aggregate.apply(
            AddExistingFileTopicNodeCommand(
                commandId = "existing-blank-path",
                treeId = "tree-existing",
                parentNodeId = "root",
                nodeId = "n5",
                title = "Blank Path",
                relativePath = " ",
                orderIndex = 0,
            ),
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, missingParent.status)
        assertTrue(missingParent.violations.any { it.code == "PARENT_MISSING" })
        assertEquals(TopicTreeCommandStatus.REJECTED, duplicate.status)
        assertTrue(duplicate.violations.any { it.code == "DUPLICATE_NODE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, nonMarkdown.status)
        assertTrue(nonMarkdown.violations.any { it.code == "INVALID_PATH" })
        assertEquals(TopicTreeCommandStatus.REJECTED, blankTitle.status)
        assertTrue(blankTitle.violations.any { it.code == "INVALID_INPUT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, negativeOrder.status)
        assertTrue(negativeOrder.violations.any { it.code == "INVALID_INPUT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, blankPath.status)
        assertTrue(blankPath.violations.any { it.code == "INVALID_INPUT" })
    }
}
