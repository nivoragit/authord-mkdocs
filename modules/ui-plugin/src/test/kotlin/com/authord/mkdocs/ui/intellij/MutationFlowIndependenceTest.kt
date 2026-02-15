package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MutationFlowIndependenceTest {
    @Test
    fun `ui mutation dispatch works with preloaded tree context without startup watcher scaffolding`() {
        val registry = InMemoryRegistry(
            TopicInstanceRef(
                instanceId = "instance-a",
                configPath = "/project/mkdocs.yml",
                docsDirPath = "/project/docs",
            ),
        )
        val orchestrator = RecordingOrchestrator()
        val applicationService = TopicTreeApplicationServiceImpl(orchestrator, registry)
        val uiService = TopicTreeUiServiceImpl(applicationService, registry)

        val result = uiService.dispatch(
            ValidateTopicTreeCommand(
                commandId = "cmd-us2-independence",
                treeId = "instance-a",
            ),
        )

        val outcome = requireSuccess(result)
        assertTrue(outcome.applied)
        assertEquals(1, orchestrator.appliedTransactions.size)
        val applied = orchestrator.appliedTransactions.single()
        assertEquals("instance-a", applied.instance.instanceId)
        assertEquals("instance-a", applied.command.treeId)
    }

    @Test
    fun `dispatch rejects when no active instance is available`() {
        val registry = EmptyRegistry()
        val orchestrator = RecordingOrchestrator()
        val applicationService = TopicTreeApplicationServiceImpl(orchestrator, registry)
        val uiService = TopicTreeUiServiceImpl(applicationService, registry)

        val result = uiService.dispatch(ValidateTopicTreeCommand("cmd-no-instance", "tree"))

        when (result) {
            is TopicGatewayResult.Success -> fail("Expected failure when no active instance is available")
            is TopicGatewayResult.Failure -> assertEquals(TopicSyncErrorCode.INSTANCE_SCOPE, result.error.code)
        }
        assertTrue(orchestrator.appliedTransactions.isEmpty())
    }

    private fun requireSuccess(result: TopicGatewayResult<TopicSyncOutcome>): TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class RecordingOrchestrator : TreeSyncOrchestrator {
    val appliedTransactions = mutableListOf<TopicSyncTransaction>()

    override fun apply(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome> {
        appliedTransactions += transaction
        return TopicGatewayResult.Success(
            TopicSyncOutcome(
                transactionId = transaction.transactionId,
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "ok",
            ),
        )
    }

    override fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Success(
            TopicSyncOutcome(transactionId, applied = false, rolledBack = true, compensated = true, message = reason),
        )
    }

    override fun compensate(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Success(
            TopicSyncOutcome(transactionId, applied = false, rolledBack = true, compensated = true, message = reason),
        )
    }
}

private class InMemoryRegistry(active: TopicInstanceRef) : InstanceRegistryPort {
    private val instances = linkedMapOf(active.instanceId to active)
    private var activeId: String = active.instanceId

    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instances[activeId])
    }

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> {
        instances[instance.instanceId] = instance
        return TopicGatewayResult.Success(Unit)
    }

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> {
        return TopicGatewayResult.Success(instances.values.toList())
    }

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        val instance = instances[instanceId]
            ?: return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Unknown instance: $instanceId"),
            )
        activeId = instanceId
        return TopicGatewayResult.Success(instance)
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instances[activeId])
    }
}

private class EmptyRegistry : InstanceRegistryPort {
    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> = TopicGatewayResult.Success(null)

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> = TopicGatewayResult.Success(Unit)

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> = TopicGatewayResult.Success(emptyList())

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "No instance: $instanceId"),
        )
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> = TopicGatewayResult.Success(null)
}
