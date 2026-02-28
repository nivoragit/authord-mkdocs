package com.authord.mkdocs.ui

import com.authord.mkdocs.defaults.command.InMemoryCommandRegistry
import com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter
import com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.command.DefaultPluginCommandBus
import com.authord.mkdocs.ports.command.PluginCommandBus
import com.authord.mkdocs.ports.preview.PreviewSyncPort
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommandType
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.vector.VectorStorePort
import com.authord.mkdocs.ui.intellij.PluginRuntimeIntegrationService
import com.authord.mkdocs.ui.intellij.InstanceRegistryService
import com.authord.mkdocs.ui.intellij.TopicTreeApplicationService
import com.authord.mkdocs.ui.intellij.TopicTreeUiService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Composition root that wires default command bus and Phase 2 topic-tree service seams.
 */
class PluginCompositionRoot {
    /**
     * Wiring bundle returned to plugin entry points.
     */
    data class Wiring(
        val commandBus: PluginCommandBus,
        val previewSyncPort: PreviewSyncPort,
        val vectorStorePort: VectorStorePort,
        val commandRegistry: InMemoryCommandRegistry,
        val mkDocsConfigGateway: MkDocsConfigGateway,
        val docsFileGateway: DocsFileGateway,
        val treeSyncOrchestrator: TreeSyncOrchestrator,
        val instanceRegistryPort: InstanceRegistryPort,
        val applicationService: TopicTreeApplicationService,
        val uiService: TopicTreeUiService,
    )

    /**
     * Creates default wiring for topic-tree command dispatch and service seams.
     */
    fun create(topicTreePort: TopicTreePort): Wiring {
        val registry = InMemoryCommandRegistry()
        val commandTypes = listOf(
            TopicTreeCommandType.ADD,
            TopicTreeCommandType.MOVE,
            TopicTreeCommandType.REMOVE,
            TopicTreeCommandType.RENAME,
            TopicTreeCommandType.REPARENT,
            TopicTreeCommandType.REORDER,
            TopicTreeCommandType.VALIDATE,
        )

        commandTypes.forEach { type ->
            registry.register(type.name) { topicTreePort.execute(it) }
        }

        val mkDocsConfigGateway = NoOpMkDocsConfigGateway()
        val docsFileGateway = NoOpDocsFileGateway()
        val treeSyncOrchestrator = NoOpTreeSyncOrchestrator()
        val instanceRegistryPort = InstanceRegistryService()
        val applicationService = DefaultTopicTreeApplicationService(treeSyncOrchestrator, instanceRegistryPort)
        val uiService = DefaultTopicTreeUiService(applicationService, instanceRegistryPort)

        return Wiring(
            commandBus = DefaultPluginCommandBus(registry),
            previewSyncPort = NoOpPreviewSyncAdapter(),
            vectorStorePort = NoOpVectorStoreAdapter(),
            commandRegistry = registry,
            mkDocsConfigGateway = mkDocsConfigGateway,
            docsFileGateway = docsFileGateway,
            treeSyncOrchestrator = treeSyncOrchestrator,
            instanceRegistryPort = instanceRegistryPort,
            applicationService = applicationService,
            uiService = uiService,
        )
    }

    /**
     * Resolves project-scoped IntelliJ runtime integration wiring for shell entry points.
     */
    fun runtimeIntegration(project: Project): PluginRuntimeIntegrationService = project.service()
}

private class NoOpMkDocsConfigGateway : MkDocsConfigGateway {
    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Configuration gateway is not wired"),
        )
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Configuration gateway is not wired"),
        )
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Configuration gateway is not wired"),
        )
    }
}

private class NoOpDocsFileGateway : DocsFileGateway {
    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Docs file gateway is not wired"),
        )
    }

    override fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Docs file gateway is not wired"),
        )
    }

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Docs file gateway is not wired"),
        )
    }

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Docs file gateway is not wired"),
        )
    }

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Docs file gateway is not wired"),
        )
    }
}

private class NoOpTreeSyncOrchestrator : TreeSyncOrchestrator {
    override fun apply(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Sync orchestrator is not wired"),
        )
    }

    override fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Sync orchestrator is not wired"),
        )
    }

    override fun compensate(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Sync orchestrator is not wired"),
        )
    }
}

private class DefaultTopicTreeApplicationService(
    private val orchestrator: TreeSyncOrchestrator,
    private val instanceRegistryPort: InstanceRegistryPort,
) : TopicTreeApplicationService {
    override fun execute(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome> {
        return orchestrator.apply(transaction)
    }

    override fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        return orchestrator.rollback(transactionId, reason)
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return instanceRegistryPort.activeInstance()
    }
}

private class DefaultTopicTreeUiService(
    private val applicationService: TopicTreeApplicationService,
    private val instanceRegistryPort: InstanceRegistryPort,
) : TopicTreeUiService {
    override fun dispatch(command: com.authord.mkdocs.ports.topic.TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome> {
        val instance = when (val active = instanceRegistryPort.activeInstance()) {
            is TopicGatewayResult.Success -> active.value
            is TopicGatewayResult.Failure -> return active
        } ?: return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "No active instance selected"),
        )
        if (command.treeId != instance.instanceId) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(
                    TopicSyncErrorCode.INSTANCE_SCOPE,
                    "Command tree '${command.treeId}' is outside active instance '${instance.instanceId}'",
                ),
            )
        }

        return applicationService.execute(
            TopicSyncTransaction(
                transactionId = command.commandId,
                instance = instance,
                command = command,
            ),
        )
    }

    override fun refreshActiveTree(): TopicGatewayResult<TopicSyncOutcome> {
        val instance = when (val active = instanceRegistryPort.activeInstance()) {
            is TopicGatewayResult.Success -> active.value
            is TopicGatewayResult.Failure -> return active
        } ?: return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "No active instance selected"),
        )

        val command = ValidateTopicTreeCommand(
            commandId = "refresh-${instance.instanceId}",
            treeId = instance.instanceId,
        )

        return applicationService.execute(
            TopicSyncTransaction(
                transactionId = command.commandId,
                instance = instance,
                command = command,
            ),
        )
    }

    override fun selectInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        return instanceRegistryPort.selectActiveInstance(instanceId)
    }
}
