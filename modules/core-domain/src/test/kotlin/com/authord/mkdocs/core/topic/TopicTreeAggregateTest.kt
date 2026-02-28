package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicNavNode
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
    fun `move node within same parent keeps expected relative order and contiguous indices`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "a", "A", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "root", "b", "B", 1))
        aggregate.apply(AddTopicNodeCommand("add-3", "tree-1", "root", "c", "C", 2))

        val result = aggregate.apply(MoveTopicNodeCommand("move-same-parent", "tree-1", "a", "root", 2))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        val rootChildren = aggregate.snapshot()
            .filter { it.parentNodeId == "root" && it.status == TopicNodeStatus.ACTIVE }
            .sortedBy { it.orderIndex }
        assertEquals(listOf("b", "a", "c"), rootChildren.map { it.nodeId })
        assertEquals(listOf(0, 1, 2), rootChildren.map { it.orderIndex })
    }

    @Test
    fun `reparent appends to new parent and normalizes both sibling groups`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "a", "A", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "root", "b", "B", 1))
        aggregate.apply(AddTopicNodeCommand("add-3", "tree-1", "root", "c", "C", 2))
        aggregate.apply(AddTopicNodeCommand("add-4", "tree-1", "a", "a1", "A1", 0))
        aggregate.apply(AddTopicNodeCommand("add-5", "tree-1", "a", "a2", "A2", 1))

        val result = aggregate.apply(ReparentTopicNodeCommand("reparent-normalize", "tree-1", "c", "a"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        val rootChildren = aggregate.snapshot()
            .filter { it.parentNodeId == "root" && it.status == TopicNodeStatus.ACTIVE }
            .sortedBy { it.orderIndex }
        assertEquals(listOf("a", "b"), rootChildren.map { it.nodeId })
        assertEquals(listOf(0, 1), rootChildren.map { it.orderIndex })

        val aChildren = aggregate.snapshot()
            .filter { it.parentNodeId == "a" && it.status == TopicNodeStatus.ACTIVE }
            .sortedBy { it.orderIndex }
        assertEquals(listOf("a1", "a2", "c"), aChildren.map { it.nodeId })
        assertEquals(listOf(0, 1, 2), aChildren.map { it.orderIndex })
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
    fun `rename updates title and rejects blank`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "n1", "Old", 0))

        val renamed = aggregate.apply(RenameTopicNodeCommand("rename-1", "tree-1", "n1", "New"))
        val invalid = aggregate.apply(RenameTopicNodeCommand("rename-2", "tree-1", "n1", " "))

        assertEquals(TopicTreeCommandStatus.SUCCESS, renamed.status)
        assertEquals("New", aggregate.snapshot().first { it.nodeId == "n1" }.title)
        assertEquals(TopicTreeCommandStatus.REJECTED, invalid.status)
        assertTrue(invalid.violations.any { it.code == "INVALID_INPUT" })
    }

    @Test
    fun `rename rejects missing node`() {
        val aggregate = TopicTreeAggregate("tree-1")

        val result = aggregate.apply(RenameTopicNodeCommand("rename-1", "tree-1", "missing", "New"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `reparent updates parent and rejects invalid states`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "a", "A", 0))
        aggregate.apply(AddTopicNodeCommand("add-2", "tree-1", "a", "b", "B", 0))
        aggregate.apply(AddTopicNodeCommand("add-3", "tree-1", "root", "c", "C", 1))

        val success = aggregate.apply(ReparentTopicNodeCommand("reparent-1", "tree-1", "c", "a"))
        val cycle = aggregate.apply(ReparentTopicNodeCommand("reparent-2", "tree-1", "a", "b"))
        val root = aggregate.apply(ReparentTopicNodeCommand("reparent-3", "tree-1", "root", "a"))
        val missing = aggregate.apply(ReparentTopicNodeCommand("reparent-4", "tree-1", "missing", "a"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, success.status)
        assertEquals("a", aggregate.snapshot().first { it.nodeId == "c" }.parentNodeId)
        assertEquals(TopicTreeCommandStatus.REJECTED, cycle.status)
        assertTrue(cycle.violations.any { it.code == "CYCLE" })
        assertEquals(TopicTreeCommandStatus.REJECTED, root.status)
        assertTrue(root.violations.any { it.code == "ROOT_REPARENT" })
        assertEquals(TopicTreeCommandStatus.REJECTED, missing.status)
        assertTrue(missing.violations.any { it.code == "NODE_MISSING" })
    }

    @Test
    fun `reparent rejects missing parent`() {
        val aggregate = TopicTreeAggregate("tree-1")
        aggregate.apply(AddTopicNodeCommand("add-1", "tree-1", "root", "child", "Child", 0))

        val result = aggregate.apply(ReparentTopicNodeCommand("reparent-missing-parent", "tree-1", "child", "missing-parent"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.violations.any { it.code == "PARENT_MISSING" })
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

    @Test
    fun `mutation service bootstrap seeds persisted nav nodes for existing-topic mutations`() {
        val service = TopicTreeMutationService()
        service.bootstrapTreeFromNav(
            treeId = "tree-bootstrap",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-existing",
                    title = "Existing",
                    path = "existing.md",
                ),
            ),
        )

        val rename = service.execute(
            RenameTopicNodeCommand(
                commandId = "rename-existing",
                treeId = "tree-bootstrap",
                nodeId = "n-existing",
                newTitle = "Existing Updated",
            ),
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, rename.status)
    }

    @Test
    fun `mutation service bootstrap is idempotent once aggregate has active nodes`() {
        val service = TopicTreeMutationService()
        service.bootstrapTreeFromNav(
            treeId = "tree-bootstrap-idempotent",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-existing",
                    title = "Existing",
                    path = "existing.md",
                ),
            ),
        )
        val created = service.execute(
            AddTopicNodeCommand(
                commandId = "add-session-node",
                treeId = "tree-bootstrap-idempotent",
                parentNodeId = "root",
                nodeId = "n-session",
                title = "Session Node",
                orderIndex = 1,
            ),
        )
        assertEquals(TopicTreeCommandStatus.SUCCESS, created.status)

        service.bootstrapTreeFromNav(
            treeId = "tree-bootstrap-idempotent",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-other",
                    title = "Other",
                    path = "other.md",
                ),
            ),
        )

        val renameSession = service.execute(
            RenameTopicNodeCommand(
                commandId = "rename-session-node",
                treeId = "tree-bootstrap-idempotent",
                nodeId = "n-session",
                newTitle = "Session Node Updated",
            ),
        )
        assertEquals(TopicTreeCommandStatus.SUCCESS, renameSession.status)
    }

    @Test
    fun `mutation service refresh replaces aggregate state with latest nav snapshot`() {
        val service = TopicTreeMutationService()
        service.bootstrapTreeFromNav(
            treeId = "tree-refresh",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-initial",
                    title = "Initial",
                    path = "initial.md",
                ),
            ),
        )

        val seededRename = service.execute(
            RenameTopicNodeCommand(
                commandId = "rename-initial",
                treeId = "tree-refresh",
                nodeId = "n-initial",
                newTitle = "Initial Updated",
            ),
        )
        assertEquals(TopicTreeCommandStatus.SUCCESS, seededRename.status)

        service.refreshTreeFromNav(
            treeId = "tree-refresh",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-fresh",
                    title = "Fresh",
                    path = "fresh.md",
                ),
            ),
        )

        val staleRename = service.execute(
            RenameTopicNodeCommand(
                commandId = "rename-stale",
                treeId = "tree-refresh",
                nodeId = "n-initial",
                newTitle = "Should Reject",
            ),
        )
        assertEquals(TopicTreeCommandStatus.REJECTED, staleRename.status)

        val freshRename = service.execute(
            RenameTopicNodeCommand(
                commandId = "rename-fresh",
                treeId = "tree-refresh",
                nodeId = "n-fresh",
                newTitle = "Fresh Updated",
            ),
        )
        assertEquals(TopicTreeCommandStatus.SUCCESS, freshRename.status)
    }

    @Test
    fun `aggregate bootstrap maps section page and external nodes from nav`() {
        val aggregate = TopicTreeAggregate("tree-bootstrap-kind-matrix")

        aggregate.bootstrapFromNav(
            nav = listOf(
                TopicNavNode(
                    nodeId = "root",
                    title = "Root Wrapper",
                    children = listOf(
                        TopicNavNode(
                            nodeId = "section-node",
                            title = "Section",
                            children = listOf(
                                TopicNavNode(
                                    nodeId = "page-node",
                                    title = "Page",
                                    path = "guides/page.md",
                                ),
                                TopicNavNode(
                                    nodeId = "external-node",
                                    title = "External",
                                    externalUrl = "https://example.com/docs",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val snapshot = aggregate.snapshot().associateBy { it.nodeId }
        assertEquals(TopicNodeKind.SECTION, snapshot.getValue("section-node").kind)
        assertEquals(TopicNodeKind.PAGE, snapshot.getValue("page-node").kind)
        assertEquals("guides/page.md", snapshot.getValue("page-node").sourcePath)
        assertEquals(TopicNodeKind.EXTERNAL_LINK, snapshot.getValue("external-node").kind)
        assertEquals("https://example.com/docs", snapshot.getValue("external-node").externalUrl)
    }

    @Test
    fun `aggregate bootstrap ignores duplicate nav node ids`() {
        val aggregate = TopicTreeAggregate("tree-bootstrap-duplicates")

        aggregate.bootstrapFromNav(
            nav = listOf(
                TopicNavNode(
                    nodeId = "dup-node",
                    title = "First",
                    path = "first.md",
                ),
                TopicNavNode(
                    nodeId = "dup-node",
                    title = "Second",
                    path = "second.md",
                ),
            ),
        )

        val duplicateNodes = aggregate.snapshot().filter { it.nodeId == "dup-node" }
        assertEquals(1, duplicateNodes.size)
        assertEquals("First", duplicateNodes.single().title)
    }

    @Suppress("UNCHECKED_CAST")
    private fun internalNodes(aggregate: TopicTreeAggregate): MutableMap<String, TopicNode> {
        val field = TopicTreeAggregate::class.java.getDeclaredField("nodes").apply { isAccessible = true }
        return field.get(aggregate) as MutableMap<String, TopicNode>
    }
}
