package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand

/**
 * Default implementation of [TopicTreeUiService] for action/drag-drop command dispatch.
 */
class TopicTreeUiServiceImpl(
    private val applicationService: TopicTreeApplicationService,
    val instanceRegistryPort: InstanceRegistryPort,
    private val scopeGuard: TopicTreeScopeGuard = TopicTreeScopeGuard(instanceRegistryPort),
) : TopicTreeUiService {
    internal fun hydrateTreeFromConfig(
        treeId: String,
        instanceId: String,
        config: MkDocsConfigDocument,
    ): TopicGatewayResult<Unit> {
        return applicationService.hydrateTreeFromConfig(
            treeId = treeId,
            instanceId = instanceId,
            config = config,
        )
    }

    override fun dispatch(command: TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome> {
        val instance = when (val scoped = scopeGuard.ensureCommandScope(command)) {
            is TopicGatewayResult.Success -> scoped.value
            is TopicGatewayResult.Failure -> return scoped
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
