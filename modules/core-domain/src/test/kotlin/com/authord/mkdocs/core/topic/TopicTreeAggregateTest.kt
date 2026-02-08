package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreeAggregateTest {
    @Test
    fun `add node mutation succeeds and exposes snapshot fields`() {
        val aggregate = TopicTreeAggregate("tree-1")
        assertEquals(0, aggregate.version)

        val result = aggregate.apply(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Intro",
                orderIndex = 0,
            )
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        assertEquals(1, result.treeVersion)
        assertEquals(1, aggregate.version)
        assertEquals("tree-1", aggregate.treeId)
        val added = aggregate.snapshot().first { it.nodeId == "n1" }
        assertEquals("Intro", added.title)
        assertEquals(2, aggregate.snapshot().size)
    }

    @Test
    fun `add node rejects missing parent`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-1",
                parentNodeId = "missing-parent",
                nodeId = "n1",
                title = "Intro",
                orderIndex = 0,
            )
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `add node rejects duplicate id`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))

        val result = aggregate.apply(
            AddTopicNodeCommand(
                commandId = "add-2",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Duplicate",
                orderIndex = 1,
            )
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "DUPLICATE_NODE" })
    }

    @Test
    fun `add node rejects invalid title or order`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(
            AddTopicNodeCommand(
                commandId = "add-invalid",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "n1",
                title = " ",
                orderIndex = 0,
            )
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "INVALID_INPUT" })
    }

    @Test
    fun `root node removal is rejected`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(
            RemoveTopicNodeCommand(
                commandId = "cmd-2",
                treeId = "tree-1",
                nodeId = "root",
            )
        )

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "ROOT_REMOVE" })
    }

    @Test
    fun `move node succeeds when parent exists and no cycle`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "root", "n2", "Node 2", 1))

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "n2", "n1", 0))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
    }

    @Test
    fun `move node rejects missing node`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "unknown", "root", 0))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `move node rejects missing parent`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "n1", "missing-parent", 0))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `move node rejects negative index`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "n1", "root", -1))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "INVALID_INDEX" })
    }

    @Test
    fun `move node rejects cycle`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "n1", "n2", "Node 2", 0))

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "n1", "n2", 0))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "CYCLE" })
    }

    @Test
    fun `move node succeeds when ancestor chain contains unresolved reference`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "root", "n2", "Node 2", 1))

        val nodes = internalNodes(aggregate)
        nodes["n1"] = nodes.getValue("n1").copy(parentNodeId = "ghost")

        val result = aggregate.apply(MoveTopicNodeCommand("move-1", "tree-1", "n2", "n1", 0))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
    }

    @Test
    fun `remove existing non-root marks node removed`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))

        val result = aggregate.apply(RemoveTopicNodeCommand("remove-1", "tree-1", "n1"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        val removed = aggregate.snapshot().first { it.nodeId == "n1" }
        assertEquals(TopicNodeStatus.REMOVED, removed.status)
    }

    @Test
    fun `remove missing node is rejected`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(RemoveTopicNodeCommand("remove-1", "tree-1", "missing"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `reorder rejects missing parent`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(ReorderTopicNodesCommand("reorder-0", "tree-1", "missing-parent", emptyList()))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `reorder validates sibling set and applies order`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "root", "n2", "Node 2", 1))

        val rejected = aggregate.apply(ReorderTopicNodesCommand("reorder-1", "tree-1", "root", listOf("n1")))
        assertEquals(TopicTreeCommandStatus.REJECTED, rejected.status)

        val success = aggregate.apply(ReorderTopicNodesCommand("reorder-2", "tree-1", "root", listOf("n2", "n1")))
        assertEquals(TopicTreeCommandStatus.SUCCESS, success.status)
        val ordered = aggregate.snapshot().filter { it.parentNodeId == "root" }
        assertEquals(0, ordered.first { it.nodeId == "n2" }.orderIndex)
        assertEquals(1, ordered.first { it.nodeId == "n1" }.orderIndex)
    }

    @Test
    fun `validate reports violations when aggregate is manually corrupted`() {
        val aggregate = TopicTreeAggregate("tree-1")

        @Suppress("UNCHECKED_CAST")
        val nodesField = TopicTreeAggregate::class.java.getDeclaredField("nodes").apply { isAccessible = true }
        val nodes = nodesField.get(aggregate) as MutableMap<String, TopicNode>
        nodes["orphan"] = TopicNode(
            nodeId = "orphan",
            parentNodeId = "missing-parent",
            title = "Orphan",
            orderIndex = 0,
        )

        val result = aggregate.apply(ValidateTopicTreeCommand("validate-1", "tree-1"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `validate reports violation when multiple roots exist`() {
        val aggregate = TopicTreeAggregate("tree-1")
        val nodes = internalNodes(aggregate)
        nodes["extra-root"] = TopicNode(
            nodeId = "extra-root",
            parentNodeId = null,
            title = "Extra Root",
            orderIndex = 1,
        )

        val result = aggregate.apply(ValidateTopicTreeCommand("validate-2", "tree-1"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "ROOT_COUNT" })
    }

    @Test
    fun `validate succeeds for a consistent tree`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Node 1", 0))

        val result = aggregate.apply(ValidateTopicTreeCommand("validate-ok", "tree-1"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        assertEquals("Validation passed", result.message)
    }

    @Test
    fun `topic tree mutation service dispatches through aggregate map`() {
        val service = TopicTreeMutationService()

        val result = service.execute(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-service",
                parentNodeId = "root",
                nodeId = "n1",
                title = "Service Node",
                orderIndex = 0,
            )
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
    }

    @Suppress("UNCHECKED_CAST")
    private fun internalNodes(aggregate: TopicTreeAggregate): MutableMap<String, TopicNode> {
        val field = TopicTreeAggregate::class.java.getDeclaredField("nodes").apply { isAccessible = true }
        return field.get(aggregate) as MutableMap<String, TopicNode>
    }
}
