package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import java.util.UUID

/**
 * Dispatches drag/drop reorder and reparent commands.
 */
class TopicTreeDragDropController(
    private val uiService: TopicTreeUiService,
    private val failureRecoveryPresenter: TopicTreeFailureRecoveryPresenter = TopicTreeFailureRecoveryPresenter(),
    private val commandIdFactory: () -> String = { "cmd-${UUID.randomUUID()}" },
) {
    /**
     * Moves one node to a new parent and order index.
     */
    fun moveTopic(
        treeId: String,
        nodeId: String,
        newParentNodeId: String,
        newOrderIndex: Int,
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Move topic",
            command = MoveTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                nodeId = nodeId,
                newParentNodeId = newParentNodeId,
                newOrderIndex = newOrderIndex,
            ),
        )
    }

    /**
     * Reparents one node while preserving order index.
     */
    fun reparentTopic(
        treeId: String,
        nodeId: String,
        newParentNodeId: String,
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Reparent topic",
            command = ReparentTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                nodeId = nodeId,
                newParentNodeId = newParentNodeId,
            ),
        )
    }

    /**
     * Applies explicit sibling ordering for one parent.
     */
    fun reorderTopics(
        treeId: String,
        parentNodeId: String,
        orderedNodeIds: List<String>,
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Reorder topics",
            command = ReorderTopicNodesCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                parentNodeId = parentNodeId,
                orderedNodeIds = orderedNodeIds,
            ),
        )
    }

    private fun dispatch(operationLabel: String, command: com.authord.mkdocs.ports.topic.TopicTreeCommand): TopicMutationDispatchResult {
        val result = uiService.dispatch(command)
        return when (result) {
            is TopicGatewayResult.Success -> TopicMutationDispatchResult(result = result, recovery = null)
            is TopicGatewayResult.Failure -> TopicMutationDispatchResult(
                result = result,
                recovery = failureRecoveryPresenter.present(operationLabel, result.error),
            )
        }
    }
}
