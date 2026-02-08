package contract.topic

import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.defaults.command.InMemoryCommandRegistry
import com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter
import com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
import com.authord.mkdocs.ports.command.DefaultPluginCommandBus
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreePortContractTest {
    @Test
    fun `command bus and topic-tree port work with default adapters`() {
        val topicTreeService = TopicTreeMutationService()
        val registry = InMemoryCommandRegistry().apply {
            register("ADD") { topicTreeService.execute(it) }
            register("RENAME") { topicTreeService.execute(it) }
        }
        val bus = DefaultPluginCommandBus(registry)
        val previewSync = NoOpPreviewSyncAdapter()
        val vectorStore = NoOpVectorStoreAdapter()

        val result = bus.dispatch(
            AddTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "child-1",
                title = "Title",
                orderIndex = 0,
            )
        )

        previewSync.onEditorScrollSemanticDelta("project-1", "docs/index.md", 10)
        vectorStore.upsert("doc-1", "content")
        val renamed = bus.dispatch(
            RenameTopicNodeCommand(
                commandId = "cmd-2",
                treeId = "tree-1",
                nodeId = "child-1",
                newTitle = "Renamed",
            )
        )

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        assertEquals(TopicTreeCommandStatus.SUCCESS, renamed.status)
        assertEquals(listOf(10), previewSync.receivedDeltas)
        assertTrue(vectorStore.search("query", 5).isEmpty())
    }
}
