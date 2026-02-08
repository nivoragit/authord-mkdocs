package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicTreeViolation
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand

enum class TopicNodeStatus {
    ACTIVE,
    REMOVED
}

data class TopicNode(
    val nodeId: String,
    val parentNodeId: String?,
    val title: String,
    val orderIndex: Int,
    val status: TopicNodeStatus = TopicNodeStatus.ACTIVE,
)

class TopicTreeAggregate(
    val treeId: String,
    private val rootNodeId: String = "root",
) {
    private val nodes = linkedMapOf(
        rootNodeId to TopicNode(rootNodeId, null, "Root", 0, TopicNodeStatus.ACTIVE),
    )

    var version: Int = 0
        private set

    fun snapshot(): List<TopicNode> = nodes.values.toList()

    fun apply(command: TopicTreeCommand): TopicTreeCommandResult {
        return when (command) {
            is AddTopicNodeCommand -> add(command)
            is MoveTopicNodeCommand -> move(command)
            is RemoveTopicNodeCommand -> remove(command)
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
        )
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

        nodes[command.nodeId] = existing.copy(
            parentNodeId = command.newParentNodeId,
            orderIndex = command.newOrderIndex,
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

    private fun reorder(command: ReorderTopicNodesCommand): TopicTreeCommandResult {
        if (!nodes.containsKey(command.parentNodeId)) {
            return rejected(command.commandId, "PARENT_MISSING", "Parent does not exist")
        }

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
        var current: String? = newParentId
        while (current != null) {
            if (current == nodeId) {
                return true
            }
            current = nodes[current]?.parentNodeId
        }
        return false
    }
}

class TopicTreeMutationService(
    private val aggregateByTreeId: MutableMap<String, TopicTreeAggregate> = mutableMapOf(),
) : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        val aggregate = aggregateByTreeId.getOrPut(command.treeId) { TopicTreeAggregate(command.treeId) }
        return aggregate.apply(command)
    }
}
