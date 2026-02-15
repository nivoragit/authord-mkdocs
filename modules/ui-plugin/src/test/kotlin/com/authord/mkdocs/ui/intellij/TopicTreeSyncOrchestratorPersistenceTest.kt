package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TopicTreeSyncOrchestratorPersistenceTest {
    @Test
    fun `add child mutation persists nav and creates markdown file`() {
        val initialConfig = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(
                    nodeId = "n-parent",
                    title = "Guide",
                    path = "guide/index.md",
                ),
            ),
        )
        val configGateway = RecordingConfigGateway(initialConfig)
        val docsGateway = RecordingDocsGateway()
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = SuccessfulPort(),
            mkDocsConfigGateway = configGateway,
            docsFileGateway = docsGateway,
        )

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-1",
                    instance = TopicInstanceRef("default", "/project/mkdocs.yml", "/project/docs"),
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-1",
                        treeId = "default",
                        targetNodeId = "n-parent",
                        childNodeId = "n-child",
                        childTitle = "Child",
                        childOrderIndex = 1,
                        childSourcePath = "guide/child.md",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(listOf("create:guide/child.md"), docsGateway.calls)

        val written = configGateway.writes.lastOrNull() ?: fail("Expected config write")
        val root = written.nav.single()
        assertEquals("Guide", root.title)
        assertEquals(null, root.path)
        assertEquals(2, root.children.size)
        assertEquals("guide/index.md", root.children[0].path)
        assertEquals("guide/child.md", root.children[1].path)
    }

    private fun requireSuccess(result: TopicGatewayResult<TopicSyncOutcome>): TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class SuccessfulPort : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            message = "Applied",
        )
    }
}

private class RecordingConfigGateway(initial: MkDocsConfigDocument) : MkDocsConfigGateway {
    private var current = initial
    val writes = mutableListOf<MkDocsConfigDocument>()

    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Success(current)
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        current = document
        writes += document
        return TopicGatewayResult.Success(Unit)
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Success("docs_dir: docs\nnav: []\n")
    }
}

private class RecordingDocsGateway : DocsFileGateway {
    val calls = mutableListOf<String>()

    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> {
        calls += "create:$relativePath"
        return TopicGatewayResult.Success(relativePath)
    }

    override fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Success(relativePath)
    }

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Success(toRelativePath)
    }

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return TopicGatewayResult.Success(toRelativePath)
    }

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        return TopicGatewayResult.Success(0)
    }
}
