package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TopicTreeDragDropControllerTest {
    @Test
    fun `move topic dispatches move command`() {
        val uiService = RecordingDragDropUiService()
        val controller = TopicTreeDragDropController(
            uiService = uiService,
            commandIdFactory = { "cmd-move-1" },
        )

        val result = controller.moveTopic(
            treeId = "tree-1",
            nodeId = "node-1",
            newParentNodeId = "section-1",
            newOrderIndex = 3,
        )

        val command = uiService.dispatched.single() as MoveTopicNodeCommand
        assertEquals("cmd-move-1", command.commandId)
        assertEquals("section-1", command.newParentNodeId)
        assertEquals(3, command.newOrderIndex)
        assertTrue(result.result is TopicGatewayResult.Success)
        assertEquals(null, result.recovery)
    }

    @Test
    fun `reorder failure returns recovery guidance`() {
        val uiService = RecordingDragDropUiService()
        uiService.nextDispatchResult = TopicGatewayResult.Failure(
            DefaultTopicSyncError(TopicSyncErrorCode.ORCHESTRATION, "Rollback completed"),
        )
        val controller = TopicTreeDragDropController(
            uiService = uiService,
            commandIdFactory = { "cmd-reorder-1" },
        )

        val result = controller.reorderTopics(
            treeId = "tree-1",
            parentNodeId = "section-1",
            orderedNodeIds = listOf("a", "b", "c"),
        )

        val command = uiService.dispatched.single() as ReorderTopicNodesCommand
        assertEquals("cmd-reorder-1", command.commandId)
        assertEquals(listOf("a", "b", "c"), command.orderedNodeIds)
        val recovery = result.recovery
        assertNotNull(recovery)
        assertTrue(recovery.summary.contains("failed safely", ignoreCase = true))
        assertTrue(recovery.guidance.contains("Rollback"))
    }
}

private class RecordingDragDropUiService : TopicTreeUiService {
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
