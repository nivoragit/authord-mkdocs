package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreeAggregateMutationTest {
    @Test
    fun `add enforces unique node ids and valid parent`() {
        val aggregate = TopicTreeAggregate("tree-mutation")

        val first = aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "n1", "Node 1", 0))
        val duplicate = aggregate.apply(AddTopicNodeCommand("add-2", "tree-mutation", "root", "n1", "Node 1 copy", 1))
        val missingParent = aggregate.apply(AddTopicNodeCommand("add-3", "tree-mutation", "ghost", "n2", "Node 2", 0))

        assertEquals(TopicTreeCommandStatus.SUCCESS, first.status)
        assertEquals(TopicTreeCommandStatus.REJECTED, duplicate.status)
        assertTrue(duplicate.violations.any { it.code == "DUPLICATE_NODE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, missingParent.status)
        assertTrue(missingParent.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `move enforces cycle safety and non-negative ordering`() {
        val aggregate = TopicTreeAggregate("tree-mutation")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "parent", "Parent", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-mutation", "parent", "child", "Child", 0))

        val cycle = aggregate.apply(MoveTopicNodeCommand("move-cycle", "tree-mutation", "parent", "child", 0))
        val negative = aggregate.apply(MoveTopicNodeCommand("move-negative", "tree-mutation", "child", "root", -1))

        assertEquals(TopicTreeCommandStatus.REJECTED, cycle.status)
        assertTrue(cycle.violations.any { it.code == "CYCLE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, negative.status)
        assertTrue(negative.violations.any { it.code == "INVALID_INDEX" })
    }

    @Test
    fun `remove marks node as removed and rejects root removal`() {
        val aggregate = TopicTreeAggregate("tree-mutation")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "node", "Node", 0))

        val removed = aggregate.apply(RemoveTopicNodeCommand("remove-1", "tree-mutation", "node"))
        val root = aggregate.apply(RemoveTopicNodeCommand("remove-root", "tree-mutation", "root"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, removed.status)
        assertEquals(TopicNodeStatus.REMOVED, aggregate.snapshot().first { it.nodeId == "node" }.status)
        assertEquals(TopicTreeCommandStatus.REJECTED, root.status)
        assertTrue(root.violations.any { it.code == "ROOT_REMOVE" })
    }

    @Test
    fun `rename requires existing node and non-blank title`() {
        val aggregate = TopicTreeAggregate("tree-mutation")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "node", "Old", 0))

        val renamed = aggregate.apply(RenameTopicNodeCommand("rename-1", "tree-mutation", "node", "New"))
        val blank = aggregate.apply(RenameTopicNodeCommand("rename-2", "tree-mutation", "node", " "))
        val missing = aggregate.apply(RenameTopicNodeCommand("rename-3", "tree-mutation", "ghost", "Name"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, renamed.status)
        assertEquals("New", aggregate.snapshot().first { it.nodeId == "node" }.title)
        assertEquals(TopicTreeCommandStatus.REJECTED, blank.status)
        assertTrue(blank.violations.any { it.code == "INVALID_INPUT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, missing.status)
        assertTrue(missing.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `reparent rejects cycles and missing parents`() {
        val aggregate = TopicTreeAggregate("tree-mutation")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "parent", "Parent", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-mutation", "parent", "child", "Child", 0))
        aggregate.apply(AddTopicNodeCommand("add-3", "tree-mutation", "root", "sibling", "Sibling", 1))

        val success = aggregate.apply(ReparentTopicNodeCommand("reparent-1", "tree-mutation", "sibling", "parent"))
        val cycle = aggregate.apply(ReparentTopicNodeCommand("reparent-2", "tree-mutation", "parent", "child"))
        val missing = aggregate.apply(ReparentTopicNodeCommand("reparent-3", "tree-mutation", "child", "ghost"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, success.status)
        assertEquals("parent", aggregate.snapshot().first { it.nodeId == "sibling" }.parentNodeId)
        assertEquals(TopicTreeCommandStatus.REJECTED, cycle.status)
        assertTrue(cycle.violations.any { it.code == "CYCLE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, missing.status)
        assertTrue(missing.violations.any { it.code == "PARENT_MISSING" })
    }

    @Test
    fun `reorder requires exact active sibling set`() {
        val aggregate = TopicTreeAggregate("tree-mutation")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-mutation", "root", "a", "A", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-mutation", "root", "b", "B", 1))

        val mismatch = aggregate.apply(ReorderTopicNodesCommand("reorder-1", "tree-mutation", "root", listOf("a")))
        val success = aggregate.apply(ReorderTopicNodesCommand("reorder-2", "tree-mutation", "root", listOf("b", "a")))

        assertEquals(TopicTreeCommandStatus.REJECTED, mismatch.status)
        assertTrue(mismatch.violations.any { it.code == "REORDER_MISMATCH" })
        assertEquals(TopicTreeCommandStatus.SUCCESS, success.status)
        assertEquals(0, aggregate.snapshot().first { it.nodeId == "b" }.orderIndex)
        assertEquals(1, aggregate.snapshot().first { it.nodeId == "a" }.orderIndex)
    }
}
