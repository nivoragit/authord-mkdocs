package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicTreeCommand

/**
 * Enforces active-instance scope for mutation commands and reconciliation flows.
 */
class TopicTreeScopeGuard(
    private val instanceRegistryPort: InstanceRegistryPort,
) {
    /**
     * Validates that [command] targets the currently selected instance.
     */
    fun ensureCommandScope(command: TopicTreeCommand): TopicGatewayResult<TopicInstanceRef> {
        val active = when (val activeResult = instanceRegistryPort.activeInstance()) {
            is TopicGatewayResult.Success -> activeResult.value
            is TopicGatewayResult.Failure -> return activeResult
        } ?: return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "No active instance selected"),
        )

        if (command.treeId != active.instanceId) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(
                    TopicSyncErrorCode.INSTANCE_SCOPE,
                    "Command tree '${command.treeId}' is outside active instance '${active.instanceId}'",
                ),
            )
        }

        return TopicGatewayResult.Success(active)
    }
}
