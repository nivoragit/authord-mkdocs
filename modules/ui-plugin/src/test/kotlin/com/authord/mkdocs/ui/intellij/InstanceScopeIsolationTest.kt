package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstanceScopeIsolationTest {
    @Test
    fun `scope guard allows mutation only for active instance`() {
        val active = TopicInstanceRef(
            instanceId = "alpha",
            configPath = "/tmp/project/mkdocs.yml",
            docsDirPath = "/tmp/project/docs",
        )
        val guard = TopicTreeScopeGuard(
            instanceRegistryPort = FixedActiveInstanceRegistry(active),
        )

        val allowed = guard.ensureCommandScope(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "alpha",
                parentNodeId = "root",
                nodeId = "n-1",
                title = "Guide",
                orderIndex = 0,
            ),
        )
        val rejected = guard.ensureCommandScope(
            AddTopicNodeCommand(
                commandId = "cmd-2",
                treeId = "beta",
                parentNodeId = "root",
                nodeId = "n-2",
                title = "Other",
                orderIndex = 0,
            ),
        )

        require(allowed is TopicGatewayResult.Success)
        assertEquals("alpha", allowed.value.instanceId)
        require(rejected is TopicGatewayResult.Failure)
        assertEquals(TopicSyncErrorCode.INSTANCE_SCOPE, rejected.error.code)
        assertTrue(rejected.error.detail.contains("active instance", ignoreCase = true))
    }
}

private class FixedActiveInstanceRegistry(
    private val active: TopicInstanceRef?,
) : InstanceRegistryPort {
    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(active)
    }

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Not required for this test"),
        )
    }

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> {
        return TopicGatewayResult.Success(listOfNotNull(active))
    }

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        return TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.UNSUPPORTED, "Not required for this test"),
        )
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(active)
    }
}
