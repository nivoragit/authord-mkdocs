package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import java.util.UUID

/**
 * Dispatches direct topic-tree action commands from non-drag UI flows.
 */
class TopicTreeActionController(
    private val uiService: TopicTreeUiService,
    private val failureRecoveryPresenter: TopicTreeFailureRecoveryPresenter = TopicTreeFailureRecoveryPresenter(),
    private val commandIdFactory: () -> String = { "cmd-${UUID.randomUUID()}" },
    private val nodeIdFactory: () -> String = { "node-${UUID.randomUUID()}" },
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
) {
    /**
     * Creates a new markdown-backed topic under [parentNodeId].
     */
    fun createTopic(
        treeId: String,
        parentNodeId: String,
        title: String,
        orderIndex: Int,
        sourcePath: String? = null,
        nodeId: String = nodeIdFactory(),
    ): TopicMutationDispatchResult {
        val normalizedSourcePath = sourcePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeRelativeMarkdownPath)

        return dispatch(
            operationLabel = "Create topic",
            command = AddTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                parentNodeId = parentNodeId,
                nodeId = nodeId,
                title = title,
                orderIndex = orderIndex,
                sourcePath = normalizedSourcePath,
            ),
        )
    }

    /**
     * Adds a new child topic under [targetNodeId].
     */
    fun addChildTopic(
        treeId: String,
        targetNodeId: String,
        title: String,
        orderIndex: Int,
        sourcePath: String? = null,
        childNodeId: String = nodeIdFactory(),
    ): TopicMutationDispatchResult {
        val normalizedSourcePath = sourcePath
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeRelativeMarkdownPath)

        return dispatch(
            operationLabel = "Add child topic",
            command = AddChildTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                targetNodeId = targetNodeId,
                childNodeId = childNodeId,
                childTitle = title,
                childOrderIndex = orderIndex,
                childSourcePath = normalizedSourcePath,
            ),
        )
    }

    /**
     * Adds an existing markdown file into nav at [parentNodeId].
     */
    fun addExistingFile(
        treeId: String,
        parentNodeId: String,
        title: String,
        relativePath: String,
        orderIndex: Int,
        nodeId: String = nodeIdFactory(),
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Add existing file",
            command = AddExistingFileTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                parentNodeId = parentNodeId,
                nodeId = nodeId,
                title = title,
                relativePath = normalizeRelativeMarkdownPath(relativePath),
                orderIndex = orderIndex,
            ),
        )
    }

    /**
     * Adds an external link node.
     */
    fun addExternalLink(
        treeId: String,
        parentNodeId: String,
        title: String,
        externalUrl: String,
        orderIndex: Int,
        nodeId: String = nodeIdFactory(),
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Add external link",
            command = AddExternalLinkTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                parentNodeId = parentNodeId,
                nodeId = nodeId,
                title = title,
                externalUrl = externalUrl.trim(),
                orderIndex = orderIndex,
            ),
        )
    }

    /**
     * Renames an existing topic node.
     */
    fun renameTopic(
        treeId: String,
        nodeId: String,
        newTitle: String,
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Rename topic",
            command = RenameTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                nodeId = nodeId,
                newTitle = newTitle,
            ),
        )
    }

    /**
     * Removes a topic node from nav participation.
     */
    fun removeTopic(
        treeId: String,
        nodeId: String,
    ): TopicMutationDispatchResult {
        return dispatch(
            operationLabel = "Remove topic",
            command = RemoveTopicNodeCommand(
                commandId = commandIdFactory(),
                treeId = treeId,
                nodeId = nodeId,
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

    private fun normalizeRelativeMarkdownPath(path: String): String {
        return pathPolicy.normalize(path).trimStart('/')
    }
}
