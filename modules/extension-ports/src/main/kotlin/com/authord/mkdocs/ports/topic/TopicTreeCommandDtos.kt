package com.authord.mkdocs.ports.topic

enum class TopicTreeCommandType {
    ADD,
    MOVE,
    REMOVE,
    REORDER,
    VALIDATE
}

sealed interface TopicTreeCommand {
    val commandId: String
    val treeId: String
    val commandType: TopicTreeCommandType
}

data class AddTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val nodeId: String,
    val title: String,
    val orderIndex: Int,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.ADD
}

data class MoveTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
    val newParentNodeId: String,
    val newOrderIndex: Int,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.MOVE
}

data class RemoveTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.REMOVE
}

data class ReorderTopicNodesCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val orderedNodeIds: List<String>,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.REORDER
}

data class ValidateTopicTreeCommand(
    override val commandId: String,
    override val treeId: String,
    val strict: Boolean = true,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.VALIDATE
}

data class TopicTreeViolation(
    val code: String,
    val detail: String,
)

enum class TopicTreeCommandStatus {
    SUCCESS,
    REJECTED,
    FAILED
}

data class TopicTreeCommandResult(
    val commandId: String,
    val status: TopicTreeCommandStatus,
    val treeVersion: Int? = null,
    val violations: List<TopicTreeViolation> = emptyList(),
    val message: String = "",
)
