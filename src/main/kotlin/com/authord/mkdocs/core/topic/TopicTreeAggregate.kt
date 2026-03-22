package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicTreeAggregateBootstrapPort
import com.authord.mkdocs.ports.topic.TopicTreeAggregateRefreshPort
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicTreeViolation
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand

/**
 * Lifecycle state for a topic node inside the aggregate.
 */
enum class TopicNodeStatus {
    ACTIVE,
    REMOVED
}

/**
 * Node kind carried by aggregate topic nodes.
 */
enum class TopicNodeKind {
    PAGE,
    SECTION,
    EXTERNAL_LINK,
}

/**
 * Aggregate node model used by the MVP mutation engine.
 */
data class TopicNode(
    val nodeId: String,
    val parentNodeId: String?,
    val title: String,
    val orderIndex: Int,
    val status: TopicNodeStatus = TopicNodeStatus.ACTIVE,
    val kind: TopicNodeKind = TopicNodeKind.PAGE,
    val sourcePath: String? = null,
    val externalUrl: String? = null,
)

/**
 * In-memory topic-tree aggregate enforcing command-based mutations.
 *
 * Usage:
 * - Construct per tree ID.
 * - Apply commands through [apply].
 * - Read immutable snapshots through [snapshot].
 */
class TopicTreeAggregate(
    val treeId: String,
    private val rootNodeId: String = "root",
) {
    private val nodes = linkedMapOf(
        rootNodeId to TopicNode(
            nodeId = rootNodeId,
            parentNodeId = null,
            title = "Root",
            orderIndex = 0,
            status = TopicNodeStatus.ACTIVE,
            kind = TopicNodeKind.SECTION,
        ),
    )

    var version: Int = 0
        private set

    /**
     * Returns a point-in-time node snapshot.
     */
    fun snapshot(): List<TopicNode> = nodes.values.toList()

    /**
     * Bootstraps aggregate state from MkDocs nav content when the aggregate is still root-only.
     */
    fun bootstrapFromNav(nav: List<TopicNavNode>) {
        if (nodes.size != 1) {
            return
        }

        fun visit(parentNodeId: String, children: List<TopicNavNode>) {
            children.forEachIndexed { index, navNode ->
                if (navNode.nodeId == rootNodeId) {
                    visit(rootNodeId, navNode.children)
                    return@forEachIndexed
                }
                if (nodes.containsKey(navNode.nodeId)) {
                    return@forEachIndexed
                }

                val normalizedPath = navNode.path
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.replace('\\', '/')
                    ?.trimStart('/')
                val normalizedExternal = navNode.externalUrl
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                val kind = when {
                    normalizedExternal != null -> TopicNodeKind.EXTERNAL_LINK
                    normalizedPath == null -> TopicNodeKind.SECTION
                    else -> TopicNodeKind.PAGE
                }

                nodes[navNode.nodeId] = TopicNode(
                    nodeId = navNode.nodeId,
                    parentNodeId = parentNodeId,
                    title = navNode.title,
                    orderIndex = index,
                    kind = kind,
                    sourcePath = if (kind == TopicNodeKind.PAGE) normalizedPath else null,
                    externalUrl = if (kind == TopicNodeKind.EXTERNAL_LINK) normalizedExternal else null,
                )

                visit(navNode.nodeId, navNode.children)
            }
        }

        visit(rootNodeId, nav)
    }

    /**
     * Executes one mutation/validation command and returns the result envelope.
     */
    fun apply(command: TopicTreeCommand): TopicTreeCommandResult {
        return when (command) {
            is AddTopicNodeCommand -> add(command)
            is AddChildTopicNodeCommand -> addChild(command)
            is AddExistingFileTopicNodeCommand -> addExistingFile(command)
            is AddExternalLinkTopicNodeCommand -> addExternalLink(command)
            is MoveTopicNodeCommand -> move(command)
            is RemoveTopicNodeCommand -> remove(command)
            is RenameTopicNodeCommand -> rename(command)
            is ReparentTopicNodeCommand -> reparent(command)
            is ReorderTopicNodesCommand -> reorder(command)
            is ValidateTopicTreeCommand -> validate(command.commandId)
        }
    }

    private fun add(command: AddTopicNodeCommand): TopicTreeCommandResult {
        if (!nodes.containsKey(command.parentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "Parent does not exist")
        }
        if (nodes.containsKey(command.nodeId)) {
            return rejected(command.commandId, "DUPLICATE_NODE", "Node already exists")
        }
        if (command.title.isBlank() || command.orderIndex < 0) {
            return rejected(command.commandId, "INVALID_INPUT", "Title/order invalid")
        }

        nodes[command.nodeId] = TopicNode(
            nodeId = command.nodeId,
            parentNodeId = command.parentNodeId,
            title = command.title,
            orderIndex = command.orderIndex,
            kind = TopicNodeKind.PAGE,
            sourcePath = command.sourcePath,
        )
        normalizeSiblingOrder(command.parentNodeId)
        return success(command.commandId)
    }

    private fun addChild(command: AddChildTopicNodeCommand): TopicTreeCommandResult {
        val target = nodes[command.targetNodeId] ?: return rejected(command.commandId, "NODE_MISSING", "Target node does not exist")
        if (target.status != TopicNodeStatus.ACTIVE) {
            return rejected(command.commandId, "NODE_REMOVED", "Target node is removed")
        }
        if (nodes.containsKey(command.childNodeId)) {
            return rejected(command.commandId, "DUPLICATE_NODE", "Node already exists")
        }
        if (command.childTitle.isBlank() || command.childOrderIndex < 0) {
            return rejected(command.commandId, "INVALID_INPUT", "Child title/order invalid")
        }

        when (target.kind) {
            TopicNodeKind.EXTERNAL_LINK -> {
                return rejected(command.commandId, "INVALID_PARENT_KIND", "Cannot add child to external link")
            }

            TopicNodeKind.SECTION -> {
                nodes[command.childNodeId] = TopicNode(
                    nodeId = command.childNodeId,
                    parentNodeId = target.nodeId,
                    title = command.childTitle,
                    orderIndex = command.childOrderIndex,
                    kind = TopicNodeKind.PAGE,
                    sourcePath = command.childSourcePath,
                )
                normalizeSiblingOrder(target.nodeId)
                return success(command.commandId)
            }

            TopicNodeKind.PAGE -> {
                val preservedPageNodeId = nextPreservedPageNodeId(target.nodeId)
                val existingChildren = childNodesOf(target.nodeId)
                existingChildren.forEach { child ->
                    nodes[child.nodeId] = child.copy(orderIndex = child.orderIndex + 1)
                }

                nodes[target.nodeId] = target.copy(
                    kind = TopicNodeKind.SECTION,
                    sourcePath = null,
                    externalUrl = null,
                )

                nodes[preservedPageNodeId] = TopicNode(
                    nodeId = preservedPageNodeId,
                    parentNodeId = target.nodeId,
                    title = target.title,
                    orderIndex = 0,
                    kind = TopicNodeKind.PAGE,
                    sourcePath = target.sourcePath,
                )
                nodes[command.childNodeId] = TopicNode(
                    nodeId = command.childNodeId,
                    parentNodeId = target.nodeId,
                    title = command.childTitle,
                    orderIndex = maxOf(1, command.childOrderIndex),
                    kind = TopicNodeKind.PAGE,
                    sourcePath = command.childSourcePath,
                )
                normalizeSiblingOrder(target.nodeId, preservedPageNodeId)
                return success(command.commandId)
            }
        }
    }

    private fun addExistingFile(command: AddExistingFileTopicNodeCommand): TopicTreeCommandResult {
        if (!nodes.containsKey(command.parentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "Parent does not exist")
        }
        if (nodes.containsKey(command.nodeId)) {
            return rejected(command.commandId, "DUPLICATE_NODE", "Node already exists")
        }
        if (command.title.isBlank() || command.orderIndex < 0 || command.relativePath.isBlank()) {
            return rejected(command.commandId, "INVALID_INPUT", "Title/path/order invalid")
        }
        if (!command.relativePath.endsWith(".md")) {
            return rejected(command.commandId, "INVALID_PATH", "Existing file path must point to markdown")
        }

        nodes[command.nodeId] = TopicNode(
            nodeId = command.nodeId,
            parentNodeId = command.parentNodeId,
            title = command.title,
            orderIndex = command.orderIndex,
            kind = TopicNodeKind.PAGE,
            sourcePath = command.relativePath,
        )
        normalizeSiblingOrder(command.parentNodeId)
        return success(command.commandId)
    }

    private fun addExternalLink(command: AddExternalLinkTopicNodeCommand): TopicTreeCommandResult {
        if (!nodes.containsKey(command.parentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "Parent does not exist")
        }
        if (nodes.containsKey(command.nodeId)) {
            return rejected(command.commandId, "DUPLICATE_NODE", "Node already exists")
        }
        if (command.title.isBlank() || command.orderIndex < 0 || command.externalUrl.isBlank()) {
            return rejected(command.commandId, "INVALID_INPUT", "Title/url/order invalid")
        }
        if (!isSupportedExternalUrl(command.externalUrl)) {
            return rejected(command.commandId, "INVALID_LINK", "External URL must be http or https")
        }

        nodes[command.nodeId] = TopicNode(
            nodeId = command.nodeId,
            parentNodeId = command.parentNodeId,
            title = command.title,
            orderIndex = command.orderIndex,
            kind = TopicNodeKind.EXTERNAL_LINK,
            externalUrl = command.externalUrl,
        )
        normalizeSiblingOrder(command.parentNodeId)
        return success(command.commandId)
    }

    private fun move(command: MoveTopicNodeCommand): TopicTreeCommandResult {
        val existing = nodes[command.nodeId] ?: return rejected(command.commandId, "NODE_MISSING", "Node does not exist")
        if (!nodes.containsKey(command.newParentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "New parent does not exist")
        }
        if (command.newOrderIndex < 0) {
            return rejected(command.commandId, "INVALID_INDEX", "Order index must be >= 0")
        }
        if (wouldCreateCycle(command.nodeId, command.newParentNodeId)) {
            return rejected(command.commandId, "CYCLE", "Move would create cycle")
        }

        relocateNode(
            existing = existing,
            newParentNodeId = command.newParentNodeId,
            requestedOrderIndex = command.newOrderIndex,
        )
        return success(command.commandId)
    }

    private fun remove(command: RemoveTopicNodeCommand): TopicTreeCommandResult {
        if (command.nodeId == rootNodeId) {
            return rejected(command.commandId, "ROOT_REMOVE", "Root node cannot be removed")
        }
        val existing = nodes[command.nodeId] ?: return rejected(command.commandId, "NODE_MISSING", "Node does not exist")
        nodes[command.nodeId] = existing.copy(status = TopicNodeStatus.REMOVED)
        return success(command.commandId)
    }

    private fun rename(command: RenameTopicNodeCommand): TopicTreeCommandResult {
        val existing = nodes[command.nodeId] ?: return rejected(command.commandId, "NODE_MISSING", "Node does not exist")
        if (command.newTitle.isBlank()) {
            return rejected(command.commandId, "INVALID_INPUT", "Title must not be blank")
        }
        nodes[command.nodeId] = existing.copy(title = command.newTitle)
        return success(command.commandId)
    }

    private fun reparent(command: ReparentTopicNodeCommand): TopicTreeCommandResult {
        if (command.nodeId == rootNodeId) {
            return rejected(command.commandId, "ROOT_REPARENT", "Root node cannot be reparented")
        }
        val existing = nodes[command.nodeId] ?: return rejected(command.commandId, "NODE_MISSING", "Node does not exist")
        if (!nodes.containsKey(command.newParentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "New parent does not exist")
        }
        if (wouldCreateCycle(command.nodeId, command.newParentNodeId)) {
            return rejected(command.commandId, "CYCLE", "Reparent would create cycle")
        }

        if (existing.parentNodeId == command.newParentNodeId) {
            return success(command.commandId)
        }

        relocateNode(
            existing = existing,
            newParentNodeId = command.newParentNodeId,
            requestedOrderIndex = orderedChildNodeIds(command.newParentNodeId).size,
        )
        return success(command.commandId)
    }

    private fun reorder(command: ReorderTopicNodesCommand): TopicTreeCommandResult {
        if (!nodes.containsKey(command.parentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "Parent does not exist")
        }

        // Reorder command must describe an exact permutation of active siblings to avoid silent drops/dupes.
        val siblingIds = nodes.values
            .filter { it.parentNodeId == command.parentNodeId && it.status == TopicNodeStatus.ACTIVE }
            .map { it.nodeId }
            .toSet()

        if (siblingIds != command.orderedNodeIds.toSet()) {
            return rejected(command.commandId, "REORDER_MISMATCH", "Reorder IDs must exactly match active siblings")
        }

        command.orderedNodeIds.forEachIndexed { index, nodeId ->
            val node = nodes.getValue(nodeId)
            nodes[nodeId] = node.copy(orderIndex = index)
        }
        return success(command.commandId)
    }

    private fun validate(commandId: String): TopicTreeCommandResult {
        val violations = mutableListOf<TopicTreeViolation>()

        val rootCount = nodes.values.count { it.parentNodeId == null }
        if (rootCount != 1) {
            violations += TopicTreeViolation("ROOT_COUNT", "Exactly one root is required")
        }

        nodes.values.filter { it.parentNodeId != null }.forEach { node ->
            if (!nodes.containsKey(node.parentNodeId)) {
                violations += TopicTreeViolation("PARENT_MISSING", "Node ${node.nodeId} has missing parent")
            }
        }

        if (violations.isNotEmpty()) {
            return TopicTreeCommandResult(
                commandId = commandId,
                status = TopicTreeCommandStatus.REJECTED,
                violations = violations,
                message = "Validation failed",
            )
        }

        return TopicTreeCommandResult(
            commandId = commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = version,
            message = "Validation passed",
        )
    }

    private fun success(commandId: String): TopicTreeCommandResult {
        version += 1
        return TopicTreeCommandResult(
            commandId = commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = version,
            message = "Applied",
        )
    }

    private fun rejected(commandId: String, code: String, detail: String): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = commandId,
            status = TopicTreeCommandStatus.REJECTED,
            violations = listOf(TopicTreeViolation(code, detail)),
            message = "Rejected",
        )
    }

    private fun wouldCreateCycle(nodeId: String, newParentId: String): Boolean {
        // Walk parent chain upward; encountering nodeId means reparent/move introduces an ancestor cycle.
        var current: String? = newParentId
        while (current != null) {
            if (current == nodeId) {
                return true
            }
            current = nodes[current]?.parentNodeId
        }
        return false
    }

    private fun childNodesOf(parentId: String): List<TopicNode> {
        return nodes.values
            .filter { it.parentNodeId == parentId && it.status == TopicNodeStatus.ACTIVE }
    }

    private fun orderedChildNodeIds(parentId: String): List<String> {
        return childNodesOf(parentId)
            .sortedWith(compareBy<TopicNode> { it.orderIndex }.thenBy { it.nodeId })
            .map { it.nodeId }
    }

    private fun applySiblingOrder(orderedNodeIds: List<String>) {
        orderedNodeIds.forEachIndexed { index, nodeId ->
            val node = nodes[nodeId] ?: return@forEachIndexed
            nodes[nodeId] = node.copy(orderIndex = index)
        }
    }

    private fun relocateNode(
        existing: TopicNode,
        newParentNodeId: String,
        requestedOrderIndex: Int,
    ) {
        val sourceParentNodeId = existing.parentNodeId
        val sourceOrderedSiblings = sourceParentNodeId?.let(::orderedChildNodeIds).orEmpty()
        val sourceIndex = sourceOrderedSiblings.indexOf(existing.nodeId)
        val movingWithinSameParent = sourceParentNodeId != null && sourceParentNodeId == newParentNodeId

        if (movingWithinSameParent) {
            val reorderedSiblings = sourceOrderedSiblings.toMutableList().apply {
                remove(existing.nodeId)
            }
            val adjustedOrderIndex = if (sourceIndex >= 0 && sourceIndex < requestedOrderIndex) {
                requestedOrderIndex - 1
            } else {
                requestedOrderIndex
            }
            val insertionIndex = adjustedOrderIndex.coerceIn(0, reorderedSiblings.size)
            reorderedSiblings.add(insertionIndex, existing.nodeId)
            nodes[existing.nodeId] = existing.copy(parentNodeId = newParentNodeId)
            applySiblingOrder(reorderedSiblings)
            return
        }

        nodes[existing.nodeId] = existing.copy(parentNodeId = newParentNodeId)
        if (sourceParentNodeId != null) {
            val sourceWithoutMovingNode = sourceOrderedSiblings.filterNot { it == existing.nodeId }
            applySiblingOrder(sourceWithoutMovingNode)
        }

        val targetOrderedSiblings = orderedChildNodeIds(newParentNodeId)
            .filterNot { it == existing.nodeId }
            .toMutableList()
        val insertionIndex = requestedOrderIndex.coerceIn(0, targetOrderedSiblings.size)
        targetOrderedSiblings.add(insertionIndex, existing.nodeId)
        applySiblingOrder(targetOrderedSiblings)
    }

    private fun normalizeSiblingOrder(parentId: String, forcedFirstNodeId: String? = null) {
        val siblings = childNodesOf(parentId)
            .sortedWith(
                compareBy<TopicNode> {
                    if (forcedFirstNodeId != null && it.nodeId == forcedFirstNodeId) -1 else 0
                }.thenBy { it.orderIndex }
                    .thenBy { it.nodeId },
            )

        siblings.forEachIndexed { index, sibling ->
            nodes[sibling.nodeId] = sibling.copy(orderIndex = index)
        }
    }

    private fun nextPreservedPageNodeId(parentNodeId: String): String {
        var suffix = 0
        var candidate = "${parentNodeId}__page"
        while (nodes.containsKey(candidate)) {
            suffix += 1
            candidate = "${parentNodeId}__page$suffix"
        }
        return candidate
    }

    private fun isSupportedExternalUrl(url: String): Boolean {
        val normalized = url.trim()
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }
}

/**
 * Default in-memory [TopicTreePort] implementation backed by per-tree aggregates.
 */
class TopicTreeMutationService(
    private val aggregateByTreeId: MutableMap<String, TopicTreeAggregate> = mutableMapOf(),
) : TopicTreePort, TopicTreeAggregateBootstrapPort, TopicTreeAggregateRefreshPort {
    /**
     * Executes command against a tree aggregate, creating aggregate state on first access.
     */
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        val aggregate = aggregateByTreeId.getOrPut(command.treeId) { TopicTreeAggregate(command.treeId) }
        return aggregate.apply(command)
    }

    /**
     * Seeds one aggregate from persisted nav when the aggregate is not yet initialized.
     */
    override fun bootstrapTreeFromNav(treeId: String, nav: List<TopicNavNode>) {
        val aggregate = aggregateByTreeId.getOrPut(treeId) { TopicTreeAggregate(treeId) }
        aggregate.bootstrapFromNav(nav)
    }

    /**
     * Replaces one aggregate from persisted/reconciled nav regardless of existing in-memory state.
     */
    override fun refreshTreeFromNav(treeId: String, nav: List<TopicNavNode>) {
        val aggregate = TopicTreeAggregate(treeId).also { it.bootstrapFromNav(nav) }
        aggregateByTreeId[treeId] = aggregate
    }
}
