package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AddChildTransformPolicyTest {
    @Test
    fun `add-child on page auto-converts parent into section and preserves original page as first child`() {
        val aggregate = TopicTreeAggregate("tree-add-child")
        aggregate.apply(
            AddTopicNodeCommand(
                commandId = "add-page",
                treeId = "tree-add-child",
                parentNodeId = "root",
                nodeId = "page-node",
                title = "Guide",
                orderIndex = 0,
                sourcePath = "guide/index.md",
            ),
        )

        val result = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "add-child",
                treeId = "tree-add-child",
                targetNodeId = "page-node",
                childNodeId = "child-node",
                childTitle = "Child",
                childOrderIndex = 1,
                childSourcePath = "guide/child.md",
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)

        val target = aggregate.snapshot().first { it.nodeId == "page-node" }
        assertEquals(TopicNodeKind.SECTION, target.kind)
        assertEquals(null, target.sourcePath)

        val children = aggregate.snapshot()
            .filter { it.parentNodeId == "page-node" && it.status == TopicNodeStatus.ACTIVE }
            .sortedBy { it.orderIndex }

        assertEquals(2, children.size)
        val preserved = children[0]
        val added = children[1]

        assertNotEquals("child-node", preserved.nodeId)
        assertEquals("Guide", preserved.title)
        assertEquals(TopicNodeKind.PAGE, preserved.kind)
        assertEquals("guide/index.md", preserved.sourcePath)

        assertEquals("child-node", added.nodeId)
        assertEquals("Child", added.title)
        assertEquals(TopicNodeKind.PAGE, added.kind)
        assertEquals("guide/child.md", added.sourcePath)
    }

    @Test
    fun `add-child rejects external link target and missing target nodes`() {
        val aggregate = TopicTreeAggregate("tree-add-child")
        aggregate.apply(
            AddExternalLinkTopicNodeCommand(
                commandId = "add-link",
                treeId = "tree-add-child",
                parentNodeId = "root",
                nodeId = "external",
                title = "Docs",
                externalUrl = "https://example.com/docs",
                orderIndex = 0,
            ),
        )

        val invalidParent = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "add-child-1",
                treeId = "tree-add-child",
                targetNodeId = "external",
                childNodeId = "child-1",
                childTitle = "Child",
                childOrderIndex = 0,
                childSourcePath = "docs/child.md",
            ),
        )
        val missing = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "add-child-2",
                treeId = "tree-add-child",
                targetNodeId = "missing",
                childNodeId = "child-2",
                childTitle = "Child",
                childOrderIndex = 0,
            ),
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, invalidParent.status)
        assertTrue(invalidParent.violations.any { it.code == "INVALID_PARENT_KIND" })

        assertEquals(TopicTreeCommandStatus.REJECTED, missing.status)
        assertTrue(missing.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `add-child rejects removed target duplicate child ids and invalid child input`() {
        val aggregate = TopicTreeAggregate("tree-add-child-errors")
        aggregate.apply(
            AddTopicNodeCommand(
                commandId = "add-page",
                treeId = "tree-add-child-errors",
                parentNodeId = "root",
                nodeId = "target",
                title = "Target",
                orderIndex = 0,
            ),
        )

        aggregate.apply(
            RemoveTopicNodeCommand(
                commandId = "remove-target",
                treeId = "tree-add-child-errors",
                nodeId = "target",
            ),
        )

        val removed = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "child-removed",
                treeId = "tree-add-child-errors",
                targetNodeId = "target",
                childNodeId = "child",
                childTitle = "Child",
                childOrderIndex = 0,
            ),
        )
        assertEquals(TopicTreeCommandStatus.REJECTED, removed.status)
        assertTrue(removed.violations.any { it.code == "NODE_REMOVED" })

        val duplicate = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "child-duplicate",
                treeId = "tree-add-child-errors",
                targetNodeId = "root",
                childNodeId = "target",
                childTitle = "Duplicate",
                childOrderIndex = 0,
            ),
        )
        assertEquals(TopicTreeCommandStatus.REJECTED, duplicate.status)
        assertTrue(duplicate.violations.any { it.code == "DUPLICATE_NODE" })

        val invalidInput = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "child-invalid",
                treeId = "tree-add-child-errors",
                targetNodeId = "root",
                childNodeId = "fresh-child",
                childTitle = " ",
                childOrderIndex = -1,
            ),
        )
        assertEquals(TopicTreeCommandStatus.REJECTED, invalidInput.status)
        assertTrue(invalidInput.violations.any { it.code == "INVALID_INPUT" })
    }

    @Test
    fun `add-child section branch preserves deterministic sibling ordering by order then node id`() {
        val aggregate = TopicTreeAggregate("tree-add-child-ordering")
        aggregate.apply(AddTopicNodeCommand("add-parent", "tree-add-child-ordering", "root", "page", "Page", 0))

        aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "convert-page",
                treeId = "tree-add-child-ordering",
                targetNodeId = "page",
                childNodeId = "z-child",
                childTitle = "Z Child",
                childOrderIndex = 1,
            ),
        )

        val sectionInsert = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "section-insert",
                treeId = "tree-add-child-ordering",
                targetNodeId = "page",
                childNodeId = "a-child",
                childTitle = "A Child",
                childOrderIndex = 1,
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, sectionInsert.status)
        val childIds = aggregate.snapshot()
            .filter { it.parentNodeId == "page" && it.status == TopicNodeStatus.ACTIVE }
            .sortedBy { it.orderIndex }
            .map { it.nodeId }
        assertEquals(listOf("page__page", "a-child", "z-child"), childIds)
    }

    @Test
    fun `add-child page transform reindexes pre-existing children and handles preserved id collision`() {
        val aggregate = TopicTreeAggregate("tree-add-child-collision")
        aggregate.apply(AddTopicNodeCommand("add-parent", "tree-add-child-collision", "root", "topic", "Topic", 0))
        aggregate.apply(AddTopicNodeCommand("reserve-preserved", "tree-add-child-collision", "root", "topic__page", "Reserved", 1))

        val nodes = internalNodes(aggregate)
        nodes["legacy-child"] = TopicNode(
            nodeId = "legacy-child",
            parentNodeId = "topic",
            title = "Legacy Child",
            orderIndex = 0,
            kind = TopicNodeKind.PAGE,
            sourcePath = "legacy.md",
        )

        val result = aggregate.apply(
            AddChildTopicNodeCommand(
                commandId = "add-child",
                treeId = "tree-add-child-collision",
                targetNodeId = "topic",
                childNodeId = "new-child",
                childTitle = "New Child",
                childOrderIndex = 1,
                childSourcePath = "new.md",
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)

        val snapshot = aggregate.snapshot()
        val preserved = snapshot.first { it.nodeId == "topic__page1" }
        assertEquals("topic", preserved.parentNodeId)
        assertEquals(TopicNodeKind.PAGE, preserved.kind)

        val legacyChild = snapshot.first { it.nodeId == "legacy-child" }
        assertEquals("topic", legacyChild.parentNodeId)
        assertTrue(legacyChild.orderIndex >= 1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun internalNodes(aggregate: TopicTreeAggregate): MutableMap<String, TopicNode> {
        val field = TopicTreeAggregate::class.java.getDeclaredField("nodes").apply { isAccessible = true }
        return field.get(aggregate) as MutableMap<String, TopicNode>
    }
}
