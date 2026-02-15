package com.authord.mkdocs.ports.topic

/**
 * Supported mutation and validation operations for topic-tree commands.
 */
enum class TopicTreeCommandType {
    ADD,
    ADD_CHILD,
    ADD_EXISTING_FILE,
    ADD_EXTERNAL_LINK,
    MOVE,
    REMOVE,
    RENAME,
    REPARENT,
    REORDER,
    VALIDATE
}

/**
 * Base command contract for topic-tree mutations.
 *
 * Contract:
 * - `commandId` uniquely identifies a request in caller scope.
 * - `treeId` identifies the target topic tree aggregate.
 * - `commandType` determines dispatch routing in command registries.
 */
sealed interface TopicTreeCommand {
    val commandId: String
    val treeId: String
    val commandType: TopicTreeCommandType
}

/**
 * Adds a new topic node under an existing parent.
 */
data class AddTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val nodeId: String,
    val title: String,
    val orderIndex: Int,
    val sourcePath: String? = null,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.ADD
}

/**
 * Adds a child node under an existing target node.
 *
 * If the target node is a page node, domain policy may transform it to a section container.
 */
data class AddChildTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val targetNodeId: String,
    val childNodeId: String,
    val childTitle: String,
    val childOrderIndex: Int,
    val childSourcePath: String? = null,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.ADD_CHILD
}

/**
 * Adds an existing markdown file into navigation at a selected location.
 */
data class AddExistingFileTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val nodeId: String,
    val title: String,
    val relativePath: String,
    val orderIndex: Int,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.ADD_EXISTING_FILE
}

/**
 * Adds an external link topic node.
 */
data class AddExternalLinkTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val nodeId: String,
    val title: String,
    val externalUrl: String,
    val orderIndex: Int,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.ADD_EXTERNAL_LINK
}

/**
 * Moves a topic node to a new parent and order index.
 */
data class MoveTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
    val newParentNodeId: String,
    val newOrderIndex: Int,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.MOVE
}

/**
 * Removes a topic node from active participation.
 */
data class RemoveTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.REMOVE
}

/**
 * Renames a topic node without changing hierarchy.
 */
data class RenameTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
    val newTitle: String,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.RENAME
}

/**
 * Reparents a node while preserving its existing order index.
 */
data class ReparentTopicNodeCommand(
    override val commandId: String,
    override val treeId: String,
    val nodeId: String,
    val newParentNodeId: String,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.REPARENT
}

/**
 * Reorders active sibling nodes under a parent.
 */
data class ReorderTopicNodesCommand(
    override val commandId: String,
    override val treeId: String,
    val parentNodeId: String,
    val orderedNodeIds: List<String>,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.REORDER
}

/**
 * Validates topic-tree invariants for a target tree.
 */
data class ValidateTopicTreeCommand(
    override val commandId: String,
    override val treeId: String,
    val strict: Boolean = true,
) : TopicTreeCommand {
    override val commandType: TopicTreeCommandType = TopicTreeCommandType.VALIDATE
}

/**
 * Structured validation violation details returned with rejected commands.
 */
data class TopicTreeViolation(
    val code: String,
    val detail: String,
)

/**
 * Result status returned by topic-tree command handling.
 */
enum class TopicTreeCommandStatus {
    SUCCESS,
    REJECTED,
    FAILED
}

/**
 * Structured command execution result envelope.
 *
 * Contract:
 * - `treeVersion` is set on successful mutations.
 * - `violations` is populated when status is `REJECTED`.
 */
data class TopicTreeCommandResult(
    val commandId: String,
    val status: TopicTreeCommandStatus,
    val treeVersion: Int? = null,
    val violations: List<TopicTreeViolation> = emptyList(),
    val message: String = "",
)
