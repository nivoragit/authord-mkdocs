package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator

/**
 * Default implementation of [TopicTreeApplicationService] backed by [TreeSyncOrchestrator].
 */
class TopicTreeApplicationServiceImpl(
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
