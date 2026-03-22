package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TopicTreeActionControllerTest {
    @Test
    fun `create topic dispatches add command with normalized path`() {
        val uiService = RecordingTopicTreeUiService()
        val controller = TopicTreeActionController(
            uiService = uiService,
            commandIdFactory = { "cmd-1" },
            nodeIdFactory = { "node-1" },
        )

        val result = controller.createTopic(
            treeId = "tree-1",
            parentNodeId = "root",
            title = "Guide",
            orderIndex = 0,
            sourcePath = "docs\\guide.md",
        )

        val command = uiService.dispatched.single() as AddTopicNodeCommand
        assertEquals("cmd-1", command.commandId)
        assertEquals("node-1", command.nodeId)
        assertEquals("docs/guide.md", command.sourcePath)
        assertTrue(result.result is TopicGatewayResult.Success)
        assertEquals(null, result.recovery)
    }

    @Test
    fun `add existing file normalizes relative markdown path`() {
        val uiService = RecordingTopicTreeUiService()
        val controller = TopicTreeActionController(
            uiService = uiService,
            commandIdFactory = { "cmd-2" },
            nodeIdFactory = { "node-2" },
        )

        controller.addExistingFile(
            treeId = "tree-1",
            parentNodeId = "root",
            title = "Legacy",
            relativePath = "\\legacy\\index.md",
            orderIndex = 2,
        )

        val command = uiService.dispatched.single() as AddExistingFileTopicNodeCommand
        assertEquals("legacy/index.md", command.relativePath)
        assertEquals("node-2", command.nodeId)
    }

    @Test
    fun `failed remove returns non-destructive recovery guidance`() {
        val uiService = RecordingTopicTreeUiService()
        uiService.nextDispatchResult = TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Node is protected"),
        )
        val controller = TopicTreeActionController(uiService = uiService, commandIdFactory = { "cmd-3" })

        val result = controller.removeTopic(
            treeId = "tree-1",
            nodeId = "node-protected",
        )

        val recovery = result.recovery
        assertNotNull(recovery)
        assertTrue(recovery.nonDestructive)
        assertTrue(recovery.summary.contains("rejected", ignoreCase = true))
        assertTrue(recovery.guidance.contains("Node is protected"))
    }
}

private class RecordingTopicTreeUiService : TopicTreeUiService {
    val dispatched = mutableListOf<TopicTreeCommand>()
    var nextDispatchResult: TopicGatewayResult<TopicSyncOutcome> = TopicGatewayResult.Success(
        TopicSyncOutcome(
            transactionId = "tx-success",
            applied = true,
            rolledBack = false,
            compensated = false,
            message = "Applied",
        ),
    )

    override fun dispatch(command: TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome> {
        dispatched += command
        return nextDispatchResult
    }

    override fun refreshActiveTree(): TopicGatewayResult<TopicSyncOutcome> = nextDispatchResult

    override fun selectInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        return TopicGatewayResult.Success(
            TopicInstanceRef(
                instanceId = instanceId,
                configPath = "/tmp/project/mkdocs.yml",
                docsDirPath = "/tmp/project/docs",
            ),
        )
    }
}
