package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [TreeSyncOrchestrator] implementation for atomic topic-tree synchronization.
 */
class TopicTreeSyncOrchestratorService(
    private val topicTreePort: TopicTreePort,
    private val mkDocsConfigGateway: MkDocsConfigGateway,
    private val docsFileGateway: DocsFileGateway,
) : TreeSyncOrchestrator {
    private data class ConfigMutationResult(
        val document: MkDocsConfigDocument,
        val fileOperations: List<TopicFileOperation>,
    )

    private data class NavNodeContext(
        val node: TopicNavNode,
        val parentNodeId: String?,
    )

    private val transactionOutcomes = ConcurrentHashMap<String, TopicSyncOutcome>()
    private val configStateByInstanceId = ConcurrentHashMap<String, MkDocsConfigDocument>()

    /**
     * Applies one topic-sync transaction and returns apply/rollback/compensation metadata.
     */
    override fun apply(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome> {
        val commandResult = topicTreePort.execute(transaction.command)
        if (commandResult.status != TopicTreeCommandStatus.SUCCESS) {
            val code = if (commandResult.status == TopicTreeCommandStatus.REJECTED) {
                TopicSyncErrorCode.VALIDATION
            } else {
                TopicSyncErrorCode.ORCHESTRATION
            }
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(code, "Topic command failed: ${commandResult.message}"),
            )
        }

        val config = when (val loaded = configDocumentFor(transaction)) {
            is TopicGatewayResult.Success -> loaded.value
            is TopicGatewayResult.Failure -> return loaded
        }
        val mutation = when (val mutated = applyCommandToConfig(config, transaction.command)) {
            is TopicGatewayResult.Success -> mutated.value
            is TopicGatewayResult.Failure -> return mutated
        }

        // Keep nav/config persistence ahead of file mutations so a file operation failure can be
        // compensated without losing canonical navigation intent.
        when (val write = mkDocsConfigGateway.writeConfig(transaction.instance, mutation.document)) {
            is TopicGatewayResult.Failure -> return write
            is TopicGatewayResult.Success -> Unit
        }
        configStateByInstanceId[transaction.instance.instanceId] = mutation.document

        // Compensation is pushed in execution order and popped in reverse order (LIFO), mirroring
        // transaction semantics for create/rename/move undo paths.
        val compensationStack = ArrayDeque<() -> TopicGatewayResult<*>>()
        val allFileOperations = transaction.fileOperations + mutation.fileOperations
        for (operation in allFileOperations) {
            when (val applyResult = applyFileOperation(transaction, operation)) {
                is TopicGatewayResult.Success -> compensationStack.addFirst(compensationFor(transaction, operation))
                is TopicGatewayResult.Failure -> {
                    val compensationSucceeded = runCompensationStack(compensationStack)
                    val outcome = TopicSyncOutcome(
                        transactionId = transaction.transactionId,
                        applied = false,
                        rolledBack = true,
                        compensated = compensationSucceeded,
                        message = "Operation failed (${operation.kind}): ${applyResult.error.detail}",
                    )
                    transactionOutcomes[transaction.transactionId] = outcome
                    return TopicGatewayResult.Success(outcome)
                }
            }
        }

        val outcome = TopicSyncOutcome(
            transactionId = transaction.transactionId,
            applied = true,
            rolledBack = false,
            compensated = false,
            message = "Applied",
        )
        transactionOutcomes[transaction.transactionId] = outcome
        return TopicGatewayResult.Success(outcome)
    }

    /**
     * Records rollback intent for a previously tracked transaction.
     */
    override fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        val prior = transactionOutcomes[transactionId]
        val outcome = TopicSyncOutcome(
            transactionId = transactionId,
            applied = false,
            rolledBack = true,
            compensated = prior?.compensated ?: true,
            message = "Rollback requested: $reason",
        )
        transactionOutcomes[transactionId] = outcome
        return TopicGatewayResult.Success(outcome)
    }

    private fun configDocumentFor(
        transaction: TopicSyncTransaction,
    ): TopicGatewayResult<MkDocsConfigDocument> {
        val instanceId = transaction.instance.instanceId
        val cached = configStateByInstanceId[instanceId]
        if (cached != null) {
            return TopicGatewayResult.Success(cached)
        }
        return when (val loaded = mkDocsConfigGateway.loadConfig(transaction.instance)) {
            is TopicGatewayResult.Success -> {
                configStateByInstanceId[instanceId] = loaded.value
                TopicGatewayResult.Success(loaded.value)
            }

            is TopicGatewayResult.Failure -> loaded
        }
    }

    private fun applyCommandToConfig(
        document: MkDocsConfigDocument,
        command: TopicTreeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        return when (command) {
            is ValidateTopicTreeCommand -> successMutation(document)
            is AddTopicNodeCommand -> addTopicToConfig(document, command)
            is AddChildTopicNodeCommand -> addChildToConfig(document, command)
            is AddExistingFileTopicNodeCommand -> addExistingFileToConfig(document, command)
            is AddExternalLinkTopicNodeCommand -> addExternalLinkToConfig(document, command)
            is RenameTopicNodeCommand -> renameNodeInConfig(document, command)
            is RemoveTopicNodeCommand -> removeNodeFromConfig(document, command)
            is ReorderTopicNodesCommand -> reorderNodesInConfig(document, command)
            is MoveTopicNodeCommand -> moveNodeInConfig(document, command.nodeId, command.newParentNodeId, command.newOrderIndex)
            is ReparentTopicNodeCommand -> moveNodeInConfig(document, command.nodeId, command.newParentNodeId, Int.MAX_VALUE)
        }
    }

    private fun addTopicToConfig(
        document: MkDocsConfigDocument,
        command: AddTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val node = TopicNavNode(
            nodeId = command.nodeId,
            title = command.title,
            path = normalizePath(command.sourcePath),
        )
        val updated = insertNode(document, command.parentNodeId, node, command.orderIndex)
        val derivedOps = node.path?.let { path ->
            listOf(TopicFileOperation(TopicFileOperationKind.CREATE, path))
        } ?: emptyList()
        return when (updated) {
            is TopicGatewayResult.Success -> successMutation(updated.value, derivedOps)
            is TopicGatewayResult.Failure -> updated
        }
    }

    private fun addExistingFileToConfig(
        document: MkDocsConfigDocument,
        command: AddExistingFileTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val node = TopicNavNode(
            nodeId = command.nodeId,
            title = command.title,
            path = normalizePath(command.relativePath),
        )
        return when (val updated = insertNode(document, command.parentNodeId, node, command.orderIndex)) {
            is TopicGatewayResult.Success -> successMutation(updated.value)
            is TopicGatewayResult.Failure -> updated
        }
    }

    private fun addExternalLinkToConfig(
        document: MkDocsConfigDocument,
        command: AddExternalLinkTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val node = TopicNavNode(
            nodeId = command.nodeId,
            title = command.title,
            externalUrl = command.externalUrl,
        )
        return when (val updated = insertNode(document, command.parentNodeId, node, command.orderIndex)) {
            is TopicGatewayResult.Success -> successMutation(updated.value)
            is TopicGatewayResult.Failure -> updated
        }
    }

    private fun addChildToConfig(
        document: MkDocsConfigDocument,
        command: AddChildTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val target = findNode(document.nav, command.targetNodeId)
            ?: return configFailure("Cannot locate target node '${command.targetNodeId}' in config nav")
        if (target.node.externalUrl != null) {
            return configFailure("Cannot add child under external link '${target.node.nodeId}'")
        }

        val resolvedPath = normalizePath(command.childSourcePath) ?: deriveChildPathFromParent(target.node, command.childTitle)
        val newChild = TopicNavNode(
            nodeId = command.childNodeId,
            title = command.childTitle,
            path = resolvedPath,
        )
        val targetChildren = target.node.children.toMutableList()
        val updatedTarget = if (target.node.path == null) {
            targetChildren.addAt(command.childOrderIndex, newChild)
            target.node.copy(children = targetChildren.toList())
        } else {
            val preservedNodeId = nextPreservedPageNodeId(target.node)
            val preservedPage = TopicNavNode(
                nodeId = preservedNodeId,
                title = target.node.title,
                path = target.node.path,
            )
            val reindexed = mutableListOf<TopicNavNode>()
            reindexed += preservedPage
            val childInsertIndex = command.childOrderIndex.coerceAtLeast(1)
            reindexed.addAt(childInsertIndex, newChild)
            targetChildren.forEach(reindexed::add)
            target.node.copy(
                path = null,
                externalUrl = null,
                children = reindexed.toList(),
            )
        }

        return when (val replaced = replaceNode(document, updatedTarget)) {
            is TopicGatewayResult.Success -> {
                val ops = resolvedPath?.let { listOf(TopicFileOperation(TopicFileOperationKind.CREATE, it)) }.orEmpty()
                successMutation(replaced.value, ops)
            }

            is TopicGatewayResult.Failure -> replaced
        }
    }

    private fun renameNodeInConfig(
        document: MkDocsConfigDocument,
        command: RenameTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val node = findNode(document.nav, command.nodeId)?.node
            ?: return configFailure("Cannot locate node '${command.nodeId}' for rename")
        return when (val replaced = replaceNode(document, node.copy(title = command.newTitle))) {
            is TopicGatewayResult.Success -> successMutation(replaced.value)
            is TopicGatewayResult.Failure -> replaced
        }
    }

    private fun removeNodeFromConfig(
        document: MkDocsConfigDocument,
        command: RemoveTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val (updatedNodes, removed) = removeNode(document.nav, command.nodeId)
        if (removed == null) {
            return configFailure("Cannot locate node '${command.nodeId}' for removal")
        }
        return successMutation(document.copy(nav = updatedNodes))
    }

    private fun reorderNodesInConfig(
        document: MkDocsConfigDocument,
        command: ReorderTopicNodesCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        if (command.parentNodeId == ROOT_NODE_ID) {
            val reordered = reorderNodeList(document.nav, command.orderedNodeIds)
            return successMutation(document.copy(nav = reordered))
        }
        val parent = findNode(document.nav, command.parentNodeId)?.node
            ?: return configFailure("Cannot locate parent '${command.parentNodeId}' for reorder")
        val reorderedChildren = reorderNodeList(parent.children, command.orderedNodeIds)
        return when (val replaced = replaceNode(document, parent.copy(children = reorderedChildren))) {
            is TopicGatewayResult.Success -> successMutation(replaced.value)
            is TopicGatewayResult.Failure -> replaced
        }
    }

    private fun moveNodeInConfig(
        document: MkDocsConfigDocument,
        nodeId: String,
        newParentNodeId: String,
        newOrderIndex: Int,
    ): TopicGatewayResult<ConfigMutationResult> {
        val (withoutSource, removedNode) = removeNode(document.nav, nodeId)
        val movingNode = removedNode ?: return configFailure("Cannot locate node '$nodeId' for move")
        val sourceDocument = document.copy(nav = withoutSource)
        return when (val inserted = insertNode(sourceDocument, newParentNodeId, movingNode, newOrderIndex)) {
            is TopicGatewayResult.Success -> successMutation(inserted.value)
            is TopicGatewayResult.Failure -> inserted
        }
    }

    private fun insertNode(
        document: MkDocsConfigDocument,
        parentNodeId: String,
        node: TopicNavNode,
        orderIndex: Int,
    ): TopicGatewayResult<MkDocsConfigDocument> {
        val normalizedIndex = orderIndex.coerceAtLeast(0)
        if (parentNodeId == ROOT_NODE_ID) {
            val updatedRoot = document.nav.toMutableList().apply {
                addAt(normalizedIndex, node)
            }
            return TopicGatewayResult.Success(document.copy(nav = updatedRoot))
        }

        val parent = findNode(document.nav, parentNodeId)?.node
            ?: return configFailure("Cannot locate parent node '$parentNodeId' in config nav")
        val updatedParentChildren = parent.children.toMutableList().apply {
            addAt(normalizedIndex, node)
        }
        return replaceNode(document, parent.copy(children = updatedParentChildren))
    }

    private fun replaceNode(
        document: MkDocsConfigDocument,
        replacement: TopicNavNode,
    ): TopicGatewayResult<MkDocsConfigDocument> {
        val (updated, changed) = replaceNode(document.nav, replacement)
        if (!changed) {
            return configFailure("Cannot locate node '${replacement.nodeId}' in config nav")
        }
        return TopicGatewayResult.Success(document.copy(nav = updated))
    }

    private fun replaceNode(
        nodes: List<TopicNavNode>,
        replacement: TopicNavNode,
    ): Pair<List<TopicNavNode>, Boolean> {
        var changed = false
        val updatedNodes = nodes.map { node ->
            when {
                node.nodeId == replacement.nodeId -> {
                    changed = true
                    replacement
                }
                node.children.isNotEmpty() -> {
                    val (updatedChildren, childChanged) = replaceNode(node.children, replacement)
                    if (childChanged) {
                        changed = true
                        node.copy(children = updatedChildren)
                    } else {
                        node
                    }
                }
                else -> node
            }
        }
        return updatedNodes to changed
    }

    private fun removeNode(
        nodes: List<TopicNavNode>,
        nodeId: String,
    ): Pair<List<TopicNavNode>, TopicNavNode?> {
        val updated = mutableListOf<TopicNavNode>()
        var removed: TopicNavNode? = null
        nodes.forEach { node ->
            if (removed == null && node.nodeId == nodeId) {
                removed = node
                return@forEach
            }
            if (removed == null && node.children.isNotEmpty()) {
                val (children, childRemoved) = removeNode(node.children, nodeId)
                if (childRemoved != null) {
                    removed = childRemoved
                    updated += node.copy(children = children)
                    return@forEach
                }
            }
            updated += node
        }
        return updated to removed
    }

    private fun findNode(
        nodes: List<TopicNavNode>,
        nodeId: String,
        parentNodeId: String? = ROOT_NODE_ID,
    ): NavNodeContext? {
        nodes.forEach { node ->
            if (node.nodeId == nodeId) {
                return NavNodeContext(node, parentNodeId)
            }
            val child = findNode(node.children, nodeId, node.nodeId)
            if (child != null) {
                return child
            }
        }
        return null
    }

    private fun reorderNodeList(
        nodes: List<TopicNavNode>,
        orderedNodeIds: List<String>,
    ): List<TopicNavNode> {
        if (orderedNodeIds.isEmpty()) {
            return nodes
        }
        val byId = nodes.associateBy { it.nodeId }
        return orderedNodeIds.mapNotNull(byId::get)
    }

    private fun nextPreservedPageNodeId(target: TopicNavNode): String {
        var suffix = 0
        var candidate = "${target.nodeId}__page"
        val existingIds = target.children.map { it.nodeId }.toSet()
        while (existingIds.contains(candidate)) {
            suffix += 1
            candidate = "${target.nodeId}__page$suffix"
        }
        return candidate
    }

    private fun deriveChildPathFromParent(parent: TopicNavNode, childTitle: String): String? {
        val parentPath = normalizePath(parent.path) ?: return null
        val parentDirectory = parentPath.substringBeforeLast('/', "")
        val slug = slugifyTitle(childTitle)
        return if (parentDirectory.isEmpty()) {
            "$slug.md"
        } else {
            "$parentDirectory/$slug.md"
        }
    }

    private fun slugifyTitle(title: String): String {
        return title
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "topic" }
    }

    private fun normalizePath(path: String?): String? {
        return path
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('\\', '/')
            ?.trimStart('/')
    }

    private fun <T> MutableList<T>.addAt(index: Int, value: T) {
        add(index.coerceIn(0, size), value)
    }

    private fun successMutation(
        document: MkDocsConfigDocument,
        fileOperations: List<TopicFileOperation> = emptyList(),
    ): TopicGatewayResult<ConfigMutationResult> {
        return TopicGatewayResult.Success(ConfigMutationResult(document, fileOperations))
    }

    private fun configFailure(detail: String): TopicGatewayResult.Failure {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.ORCHESTRATION, detail),
        )
    }

    /**
     * Records compensation intent for transactions that cannot be fully rolled back.
     */
    override fun compensate(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        val outcome = TopicSyncOutcome(
            transactionId = transactionId,
            applied = false,
            rolledBack = true,
            compensated = true,
            message = "Compensation requested: $reason",
        )
        transactionOutcomes[transactionId] = outcome
        return TopicGatewayResult.Success(outcome)
    }

    private fun applyFileOperation(
        transaction: TopicSyncTransaction,
        operation: TopicFileOperation,
    ): TopicGatewayResult<*> {
        return when (operation.kind) {
            TopicFileOperationKind.CREATE -> docsFileGateway.createMarkdownFile(
                instance = transaction.instance,
                relativePath = operation.sourcePath,
                initialContent = "",
            )

            TopicFileOperationKind.DELETE -> docsFileGateway.deleteMarkdownFile(
                instance = transaction.instance,
                relativePath = operation.sourcePath,
                mode = TopicDeleteMode.RECOVERABLE,
            )

            TopicFileOperationKind.RENAME -> {
                val targetPath = operation.targetPath
                    ?: return TopicGatewayResult.Failure(
                        DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Rename operation missing target path"),
                    )
                docsFileGateway.renameMarkdownFile(
                    instance = transaction.instance,
                    fromRelativePath = operation.sourcePath,
                    toRelativePath = targetPath,
                )
            }

            TopicFileOperationKind.MOVE -> {
                val targetPath = operation.targetPath
                    ?: return TopicGatewayResult.Failure(
                        DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Move operation missing target path"),
                    )
                docsFileGateway.moveMarkdownFile(
                    instance = transaction.instance,
                    fromRelativePath = operation.sourcePath,
                    toRelativePath = targetPath,
                )
            }

            TopicFileOperationKind.REWRITE_LINKS -> {
                val targetPath = operation.targetPath
                    ?: return TopicGatewayResult.Failure(
                        DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Rewrite operation missing target path"),
                    )
                docsFileGateway.rewriteRelativeMarkdownLinks(
                    instance = transaction.instance,
                    fromRelativePath = operation.sourcePath,
                    toRelativePath = targetPath,
                )
            }
        }
    }

    private fun compensationFor(
        transaction: TopicSyncTransaction,
        operation: TopicFileOperation,
    ): () -> TopicGatewayResult<*> {
        return when (operation.kind) {
            TopicFileOperationKind.CREATE -> {
                {
                    docsFileGateway.deleteMarkdownFile(
                        instance = transaction.instance,
                        relativePath = operation.sourcePath,
                        mode = TopicDeleteMode.RECOVERABLE,
                    )
                }
            }

            TopicFileOperationKind.RENAME -> {
                {
                    val targetPath = operation.targetPath
                    if (targetPath == null) {
                        TopicGatewayResult.Failure(
                            DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Rename compensation missing target path"),
                        )
                    } else {
                        docsFileGateway.renameMarkdownFile(
                            instance = transaction.instance,
                            fromRelativePath = targetPath,
                            toRelativePath = operation.sourcePath,
                        )
                    }
                }
            }

            TopicFileOperationKind.MOVE -> {
                {
                    val targetPath = operation.targetPath
                    if (targetPath == null) {
                        TopicGatewayResult.Failure(
                            DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Move compensation missing target path"),
                        )
                    } else {
                        docsFileGateway.moveMarkdownFile(
                            instance = transaction.instance,
                            fromRelativePath = targetPath,
                            toRelativePath = operation.sourcePath,
                        )
                    }
                }
            }

            TopicFileOperationKind.DELETE,
            TopicFileOperationKind.REWRITE_LINKS,
            -> {
                // Delete recovery and link-rewrite rollback are handled via explicit recovery flows
                // and deterministic reconciliation; no synthetic inverse operation is safe here.
                { TopicGatewayResult.Success(Unit) }
            }
        }
    }

    private fun runCompensationStack(compensationStack: ArrayDeque<() -> TopicGatewayResult<*>>): Boolean {
        var allSucceeded = true
        while (compensationStack.isNotEmpty()) {
            val compensation = compensationStack.removeFirst()
            if (compensation() is TopicGatewayResult.Failure) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private companion object {
        private const val ROOT_NODE_ID: String = "root"
    }
}
