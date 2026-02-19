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
import com.authord.mkdocs.ports.topic.TopicTreeAggregateBootstrapPort
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator
import com.authord.mkdocs.runtime.MarkdownHeadingSupport
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
        val config = when (val loaded = configDocumentFor(transaction)) {
            is TopicGatewayResult.Success -> loaded.value
            is TopicGatewayResult.Failure -> return loaded
        }
        hydrateAggregateFromConfig(
            treeId = transaction.command.treeId,
            instanceId = transaction.instance.instanceId,
            config = config,
        )

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
        val mutation = when (val mutated = applyCommandToConfig(config, transaction.command)) {
            is TopicGatewayResult.Success -> mutated.value
            is TopicGatewayResult.Failure -> return mutated
        }

        // In no-nav mode we treat filesystem hierarchy as the source of truth and avoid writing
        // synthetic nav content into mkdocs.yml.
        if (config.navPresent) {
            // Keep nav/config persistence ahead of file mutations so a file operation failure can
            // be compensated without losing canonical navigation intent.
            when (val write = mkDocsConfigGateway.writeConfig(transaction.instance, mutation.document)) {
                is TopicGatewayResult.Failure -> return write
                is TopicGatewayResult.Success -> Unit
            }
        }
        configStateByInstanceId[transaction.instance.instanceId] = mutation.document

        // Compensation is pushed in execution order and popped in reverse order (LIFO), mirroring
        // transaction semantics for create/rename/move undo paths.
        val compensationStack = ArrayDeque<() -> TopicGatewayResult<*>>()
        val allFileOperations = (transaction.fileOperations + mutation.fileOperations).distinct()
        for (operation in allFileOperations) {
            when (val applyResult = applyFileOperation(transaction, operation, mutation.document)) {
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

        when (val titleSync = synchronizeMarkdownHeadingForCommand(transaction, mutation.document)) {
            is TopicGatewayResult.Success -> Unit
            is TopicGatewayResult.Failure -> {
                val compensationSucceeded = runCompensationStack(compensationStack)
                val outcome = TopicSyncOutcome(
                    transactionId = transaction.transactionId,
                    applied = false,
                    rolledBack = true,
                    compensated = compensationSucceeded,
                    message = "Heading synchronization failed: ${titleSync.error.detail}",
                )
                transactionOutcomes[transaction.transactionId] = outcome
                return TopicGatewayResult.Success(outcome)
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

    internal fun hydrateAggregateFromConfig(
        treeId: String,
        instanceId: String,
        config: MkDocsConfigDocument,
    ) {
        configStateByInstanceId[instanceId] = config
        (topicTreePort as? TopicTreeAggregateBootstrapPort)?.bootstrapTreeFromNav(
            treeId = treeId,
            nav = config.nav,
        )
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
        val resolvedPath = normalizePath(command.sourcePath)
            ?: derivePathForNewNode(document, command.parentNodeId, command.title)
        val node = TopicNavNode(
            nodeId = command.nodeId,
            title = command.title,
            path = resolvedPath,
        )
        val updated = insertNode(document, command.parentNodeId, node, command.orderIndex)
        val derivedOps = listOf(TopicFileOperation(TopicFileOperationKind.CREATE, resolvedPath))
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
        val noNavFolderHierarchy = !document.navPresent
        val target = resolveNodeContext(document.nav, command.targetNodeId)
            ?: return configFailure("Cannot locate target node '${command.targetNodeId}' in config nav")
        if (target.node.externalUrl != null) {
            return configFailure("Cannot add child under external link '${target.node.nodeId}'")
        }

        val resolvedPath = normalizePath(command.childSourcePath)
            ?: derivePathForNewNode(document, target.node.nodeId, command.childTitle)
        val newChild = TopicNavNode(
            nodeId = command.childNodeId,
            title = command.childTitle,
            path = resolvedPath,
        )
        val targetChildren = target.node.children.toMutableList()
        val extraOps = mutableListOf<TopicFileOperation>()
        val updatedTarget = if (target.node.path == null) {
            targetChildren.addAt(command.childOrderIndex, newChild)
            target.node.copy(children = targetChildren.toList())
        } else {
            val originalTargetPath = normalizePath(target.node.path)
            val preservedPagePath = if (noNavFolderHierarchy && originalTargetPath != null) {
                val sectionIndexPath = ensureUniquePath(
                    basePath = joinPath(deriveNoNavDirectoryFromPagePath(originalTargetPath), "index.md"),
                    existingPaths = collectAllPaths(document.nav) - originalTargetPath,
                )
                if (sectionIndexPath != originalTargetPath) {
                    extraOps += TopicFileOperation(TopicFileOperationKind.MOVE, originalTargetPath, sectionIndexPath)
                    extraOps += TopicFileOperation(TopicFileOperationKind.REWRITE_LINKS, originalTargetPath, sectionIndexPath)
                }
                sectionIndexPath
            } else {
                target.node.path
            }
            val preservedNodeId = nextPreservedPageNodeId(target.node)
            val preservedPage = TopicNavNode(
                nodeId = preservedNodeId,
                title = target.node.title,
                path = preservedPagePath,
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
                val ops = buildList {
                    addAll(extraOps)
                    add(TopicFileOperation(TopicFileOperationKind.CREATE, resolvedPath))
                }
                successMutation(replaced.value, ops)
            }

            is TopicGatewayResult.Failure -> replaced
        }
    }

    private fun renameNodeInConfig(
        document: MkDocsConfigDocument,
        command: RenameTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val node = resolveNodeContext(document.nav, command.nodeId)?.node
            ?: return configFailure("Cannot locate node '${command.nodeId}' for rename")
        val currentPath = normalizePath(node.path)

        if (currentPath == null && !document.navPresent) {
            val currentDirectory = deriveNoNavDirectoryForNode(node)
            if (currentDirectory != null) {
                val renamedDirectory = joinPath(
                    currentDirectory.substringBeforeLast('/', ""),
                    slugifyTitle(command.newTitle),
                )
                if (renamedDirectory != currentDirectory) {
                    val pathRewrites = deriveDirectoryRewriteMap(node, currentDirectory, renamedDirectory)
                    val rewrittenNode = rewriteNodePaths(node, pathRewrites).copy(title = command.newTitle)
                    return when (val replaced = replaceNode(document, rewrittenNode)) {
                        is TopicGatewayResult.Success -> {
                            val operations = pathRewrites.entries
                                .sortedBy { it.key }
                                .flatMap { (sourcePath, targetPath) ->
                                    listOf(
                                        TopicFileOperation(TopicFileOperationKind.RENAME, sourcePath, targetPath),
                                        TopicFileOperation(TopicFileOperationKind.REWRITE_LINKS, sourcePath, targetPath),
                                    )
                                }
                            successMutation(replaced.value, operations)
                        }
                        is TopicGatewayResult.Failure -> replaced
                    }
                }
            }
        }

        val renamedPath = currentPath?.let { deriveRenamedPath(document, it, command.newTitle) }
        val updatedNode = node.copy(
            title = command.newTitle,
            path = renamedPath ?: node.path,
        )
        return when (val replaced = replaceNode(document, updatedNode)) {
            is TopicGatewayResult.Success -> {
                val operations = if (currentPath != null && renamedPath != null && currentPath != renamedPath) {
                    listOf(
                        TopicFileOperation(TopicFileOperationKind.RENAME, currentPath, renamedPath),
                        TopicFileOperation(TopicFileOperationKind.REWRITE_LINKS, currentPath, renamedPath),
                    )
                } else {
                    emptyList()
                }
                successMutation(replaced.value, operations)
            }
            is TopicGatewayResult.Failure -> replaced
        }
    }

    private fun removeNodeFromConfig(
        document: MkDocsConfigDocument,
        command: RemoveTopicNodeCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        val resolvedNodeId = resolveNodeContext(document.nav, command.nodeId)?.node?.nodeId ?: command.nodeId
        val (updatedNodes, removed) = removeNode(document.nav, resolvedNodeId)
        if (removed == null) {
            return configFailure("Cannot locate node '${command.nodeId}' for removal")
        }
        val fileOperations = collectPaths(removed)
            .distinct()
            .map { path -> TopicFileOperation(TopicFileOperationKind.DELETE, path) }
        return successMutation(document.copy(nav = updatedNodes), fileOperations)
    }

    private fun reorderNodesInConfig(
        document: MkDocsConfigDocument,
        command: ReorderTopicNodesCommand,
    ): TopicGatewayResult<ConfigMutationResult> {
        if (command.parentNodeId == ROOT_NODE_ID) {
            val reordered = reorderNodeList(document.nav, command.orderedNodeIds)
            return successMutation(document.copy(nav = reordered))
        }
        val parent = resolveNodeContext(document.nav, command.parentNodeId)?.node
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
        val sourceContext = resolveNodeContext(document.nav, nodeId)
            ?: return configFailure("Cannot locate node '$nodeId' for move")
        val resolvedSourceNodeId = sourceContext.node.nodeId
        val (effectiveWithoutSource, removedNode) = removeNode(document.nav, resolvedSourceNodeId)
        val movingNode = removedNode ?: return configFailure("Cannot locate node '$nodeId' for move")
        val fileOperations = mutableListOf<TopicFileOperation>()
        val currentPath = normalizePath(sourceContext.node.path)
        val nodeToInsert = if (currentPath != null) {
            val updatedPath = run {
                val targetDirectory = resolveDirectoryForParent(
                    nodes = effectiveWithoutSource,
                    parentNodeId = newParentNodeId,
                    noNavFolderHierarchy = !document.navPresent,
                )
                val targetFileName = currentPath.substringAfterLast('/')
                val baseTargetPath = joinPath(targetDirectory, targetFileName)
                ensureUniquePath(baseTargetPath, collectAllPaths(effectiveWithoutSource))
            }
            if (updatedPath != currentPath) {
                fileOperations += TopicFileOperation(TopicFileOperationKind.MOVE, currentPath, updatedPath)
                fileOperations += TopicFileOperation(TopicFileOperationKind.REWRITE_LINKS, currentPath, updatedPath)
            }
            movingNode.copy(path = updatedPath)
        } else if (!document.navPresent) {
            val sourceDirectory = deriveNoNavDirectoryForNode(sourceContext.node)
            if (sourceDirectory != null) {
                val targetParentDirectory = resolveDirectoryForParent(
                    nodes = effectiveWithoutSource,
                    parentNodeId = newParentNodeId,
                    noNavFolderHierarchy = true,
                )
                val targetDirectory = joinPath(targetParentDirectory, sourceDirectory.substringAfterLast('/'))
                if (targetDirectory != sourceDirectory) {
                    val pathRewrites = deriveDirectoryRewriteMap(movingNode, sourceDirectory, targetDirectory)
                    pathRewrites.entries
                        .sortedBy { it.key }
                        .forEach { (sourcePath, targetPath) ->
                            fileOperations += TopicFileOperation(TopicFileOperationKind.MOVE, sourcePath, targetPath)
                            fileOperations += TopicFileOperation(TopicFileOperationKind.REWRITE_LINKS, sourcePath, targetPath)
                        }
                    rewriteNodePaths(movingNode, pathRewrites)
                } else {
                    movingNode
                }
            } else {
                movingNode
            }
        } else {
            movingNode
        }
        val sourceDocument = document.copy(nav = effectiveWithoutSource)
        return when (val inserted = insertNode(sourceDocument, newParentNodeId, nodeToInsert, newOrderIndex)) {
            is TopicGatewayResult.Success -> {
                successMutation(inserted.value, fileOperations)
            }
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

        val parent = resolveNodeContext(document.nav, parentNodeId)?.node
            ?: return configFailure("Cannot locate parent node '$parentNodeId' in config nav")
        val updatedParentChildren = parent.children.toMutableList().apply {
            addAt(normalizedIndex, node)
        }
        return replaceNode(document, parent.copy(children = updatedParentChildren))
    }

    private fun resolveNodeContext(nodes: List<TopicNavNode>, requestedNodeId: String): NavNodeContext? {
        val direct = findNode(nodes, requestedNodeId)
        if (direct != null) {
            return direct
        }

        val pageAliasPath = pagePathFromAlias(requestedNodeId)
        if (pageAliasPath != null) {
            val byPath = findNodeByPath(nodes, pageAliasPath)
            if (byPath != null) {
                return byPath
            }
        }

        val sectionAliasDirectory = sectionDirectoryFromAlias(requestedNodeId)
        if (sectionAliasDirectory != null) {
            val byDirectory = findSectionNodeByDirectory(nodes, sectionAliasDirectory)
            if (byDirectory != null) {
                return byDirectory
            }
        }

        return null
    }

    private fun pagePathFromAlias(nodeId: String): String? {
        if (!nodeId.startsWith("page:")) {
            return null
        }
        return normalizePath(nodeId.removePrefix("page:"))
    }

    private fun sectionDirectoryFromAlias(nodeId: String): String? {
        if (!nodeId.startsWith("section:")) {
            return null
        }
        return normalizePath(nodeId.removePrefix("section:"))
    }

    private fun findNodeByPath(
        nodes: List<TopicNavNode>,
        path: String,
        parentNodeId: String? = ROOT_NODE_ID,
    ): NavNodeContext? {
        val normalizedTarget = normalizePath(path) ?: return null
        nodes.forEach { node ->
            val normalizedNodePath = normalizePath(node.path)
            if (normalizedNodePath == normalizedTarget) {
                return NavNodeContext(node, parentNodeId)
            }
            val child = findNodeByPath(node.children, normalizedTarget, node.nodeId)
            if (child != null) {
                return child
            }
        }
        return null
    }

    private fun findSectionNodeByDirectory(
        nodes: List<TopicNavNode>,
        directory: String,
        parentNodeId: String? = ROOT_NODE_ID,
    ): NavNodeContext? {
        val normalizedDirectory = normalizePath(directory) ?: return null
        nodes.forEach { node ->
            val childMatch = findSectionNodeByDirectory(node.children, normalizedDirectory, node.nodeId)
            if (childMatch != null) {
                return childMatch
            }
            if (isDirectoryRepresentativeNode(node, normalizedDirectory)) {
                return NavNodeContext(node, parentNodeId)
            }
        }
        return null
    }

    private fun isDirectoryRepresentativeNode(node: TopicNavNode, normalizedDirectory: String): Boolean {
        val normalizedPath = normalizePath(node.path)
        if (normalizedPath != null) {
            return false
        }
        val indexPath = joinPath(normalizedDirectory, "index.md")
        return collectPaths(node).any { childPath ->
            val normalizedChildPath = normalizePath(childPath) ?: return@any false
            normalizedChildPath == indexPath
        }
    }

    private fun deriveNoNavDirectoryForNode(node: TopicNavNode): String? {
        val normalizedPath = normalizePath(node.path)
        if (normalizedPath != null) {
            return deriveNoNavDirectoryFromPagePath(normalizedPath)
        }
        val directSectionDirectory = sectionDirectoryFromNodeId(node.nodeId)
        if (directSectionDirectory != null) {
            return directSectionDirectory
        }
        val subtreePaths = collectPaths(node)
            .mapNotNull(::normalizePath)
        val indexPath = subtreePaths.firstOrNull { isIndexMarkdownPath(it) }
        if (indexPath != null) {
            return indexPath.substringBeforeLast('/', "")
        }
        return subtreePaths.firstOrNull()?.substringBeforeLast('/', "")
    }

    private fun deriveDirectoryRewriteMap(node: TopicNavNode, fromDirectory: String, toDirectory: String): Map<String, String> {
        val normalizedFrom = normalizePath(fromDirectory)?.trimEnd('/') ?: return emptyMap()
        val normalizedTo = normalizePath(toDirectory)?.trimEnd('/') ?: return emptyMap()
        if (normalizedFrom == normalizedTo) {
            return emptyMap()
        }
        val rewrites = linkedMapOf<String, String>()
        collectPaths(node)
            .mapNotNull(::normalizePath)
            .forEach { path ->
                relocatePathByDirectory(path, normalizedFrom, normalizedTo)?.let { relocated ->
                    if (relocated != path) {
                        rewrites[path] = relocated
                    }
                }
            }
        return rewrites
    }

    private fun relocatePathByDirectory(path: String, fromDirectory: String, toDirectory: String): String? {
        val normalizedPath = normalizePath(path) ?: return null
        val fromPrefix = "${fromDirectory.trimEnd('/')}/"
        if (!normalizedPath.startsWith(fromPrefix)) {
            return null
        }
        val suffix = normalizedPath.removePrefix(fromPrefix)
        return joinPath(toDirectory.trimEnd('/'), suffix)
    }

    private fun rewriteNodePaths(node: TopicNavNode, pathRewrites: Map<String, String>): TopicNavNode {
        val normalizedPath = normalizePath(node.path)
        val rewrittenPath = if (normalizedPath != null && pathRewrites.containsKey(normalizedPath)) {
            pathRewrites.getValue(normalizedPath)
        } else {
            node.path
        }
        return node.copy(
            path = rewrittenPath,
            children = node.children.map { child -> rewriteNodePaths(child, pathRewrites) },
        )
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

    private fun derivePathForNewNode(
        document: MkDocsConfigDocument,
        parentNodeId: String,
        title: String,
    ): String {
        val parentDirectory = resolveDirectoryForParent(
            nodes = document.nav,
            parentNodeId = parentNodeId,
            noNavFolderHierarchy = !document.navPresent,
        )
        val candidate = joinPath(parentDirectory, "${slugifyTitle(title)}.md")
        return ensureUniquePath(candidate, collectAllPaths(document.nav))
    }

    private fun deriveRenamedPath(
        document: MkDocsConfigDocument,
        currentPath: String,
        title: String,
    ): String {
        val currentDirectory = currentPath.substringBeforeLast('/', "")
        val renamedCandidate = joinPath(currentDirectory, "${slugifyTitle(title)}.md")
        val existing = collectAllPaths(document.nav) - currentPath
        return ensureUniquePath(renamedCandidate, existing)
    }

    private fun resolveDirectoryForParent(
        nodes: List<TopicNavNode>,
        parentNodeId: String,
        noNavFolderHierarchy: Boolean = false,
    ): String {
        if (parentNodeId == ROOT_NODE_ID) {
            return ""
        }

        val parent = resolveNodeContext(nodes, parentNodeId) ?: return ""
        if (noNavFolderHierarchy) {
            sectionDirectoryFromNodeId(parent.node.nodeId)?.let { return it }
        }
        val directPath = normalizePath(parent.node.path)
        if (directPath != null) {
            return if (noNavFolderHierarchy) {
                deriveNoNavDirectoryFromPagePath(directPath)
            } else {
                directPath.substringBeforeLast('/', "")
            }
        }

        val childPath = firstPathInSubtree(parent.node.children)
        if (childPath != null) {
            return childPath.substringBeforeLast('/', "")
        }

        val ancestorId = parent.parentNodeId ?: return ""
        return resolveDirectoryForParent(
            nodes = nodes,
            parentNodeId = ancestorId,
            noNavFolderHierarchy = noNavFolderHierarchy,
        )
    }

    private fun sectionDirectoryFromNodeId(nodeId: String): String? {
        val prefix = "section:"
        if (!nodeId.startsWith(prefix)) {
            return null
        }
        return normalizePath(nodeId.removePrefix(prefix))
    }

    private fun deriveNoNavDirectoryFromPagePath(pagePath: String): String {
        val normalized = normalizePath(pagePath) ?: return ""
        val directory = normalized.substringBeforeLast('/', "")
        val fileName = normalized.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        return if (stem.equals("index", ignoreCase = true)) {
            directory
        } else {
            joinPath(directory, stem)
        }
    }

    private fun isIndexMarkdownPath(path: String): Boolean {
        return path.equals("index.md", ignoreCase = true) || path.endsWith("/index.md", ignoreCase = true)
    }

    private fun firstPathInSubtree(nodes: List<TopicNavNode>): String? {
        nodes.forEach { node ->
            val direct = normalizePath(node.path)
            if (direct != null) {
                return direct
            }
            val nested = firstPathInSubtree(node.children)
            if (nested != null) {
                return nested
            }
        }
        return null
    }

    private fun collectAllPaths(nodes: List<TopicNavNode>): Set<String> {
        val collected = linkedSetOf<String>()

        fun visit(node: TopicNavNode) {
            normalizePath(node.path)?.let(collected::add)
            node.children.forEach(::visit)
        }

        nodes.forEach(::visit)
        return collected
    }

    private fun collectPaths(node: TopicNavNode): List<String> {
        val collected = mutableListOf<String>()

        fun visit(current: TopicNavNode) {
            normalizePath(current.path)?.let(collected::add)
            current.children.forEach(::visit)
        }

        visit(node)
        return collected
    }

    private fun ensureUniquePath(basePath: String, existingPaths: Set<String>): String {
        val normalizedBase = normalizePath(basePath) ?: "topic.md"
        if (!existingPaths.contains(normalizedBase)) {
            return normalizedBase
        }

        val directory = normalizedBase.substringBeforeLast('/', "")
        val fileName = normalizedBase.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "md")
        var suffix = 2

        while (true) {
            val candidate = joinPath(directory, "$stem-$suffix.$extension")
            if (!existingPaths.contains(candidate)) {
                return candidate
            }
            suffix += 1
        }
    }

    private fun joinPath(directory: String, fileName: String): String {
        return if (directory.isBlank()) {
            fileName
        } else {
            "${directory.trimEnd('/')}/$fileName"
        }
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
        document: MkDocsConfigDocument,
    ): TopicGatewayResult<*> {
        return when (operation.kind) {
            TopicFileOperationKind.CREATE -> docsFileGateway.createMarkdownFile(
                instance = transaction.instance,
                relativePath = operation.sourcePath,
                initialContent = initialContentForCreate(document, operation.sourcePath),
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

    private fun synchronizeMarkdownHeadingForCommand(
        transaction: TopicSyncTransaction,
        document: MkDocsConfigDocument,
    ): TopicGatewayResult<String> {
        val renameCommand = transaction.command as? RenameTopicNodeCommand
            ?: return TopicGatewayResult.Success("")
        val resolved = resolveNodeContext(document.nav, renameCommand.nodeId)?.node
            ?: return TopicGatewayResult.Success("")
        val normalizedPath = normalizePath(resolved.path)
            ?: return TopicGatewayResult.Success("")
        return docsFileGateway.upsertMarkdownTitleHeading(
            instance = transaction.instance,
            relativePath = normalizedPath,
            title = resolved.title,
        )
    }

    private fun initialContentForCreate(document: MkDocsConfigDocument, sourcePath: String): String {
        val normalizedPath = normalizePath(sourcePath) ?: return ""
        val resolvedTitle = findTitleByPath(document.nav, normalizedPath)
            ?: return ""
        return MarkdownHeadingSupport.defaultTopicContent(resolvedTitle)
    }

    private fun findTitleByPath(nodes: List<TopicNavNode>, normalizedPath: String): String? {
        nodes.forEach { node ->
            if (normalizePath(node.path) == normalizedPath) {
                return node.title
            }
            val nested = findTitleByPath(node.children, normalizedPath)
            if (nested != null) {
                return nested
            }
        }
        return null
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
