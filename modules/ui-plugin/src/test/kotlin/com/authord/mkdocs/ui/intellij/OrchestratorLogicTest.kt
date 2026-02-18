package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrchestratorLogicTest {

    @Test
    fun `apply generates file creation ops for AddChildTopicNodeCommand`() {
        val docsGateway = StubDocsFileGateway()
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = StubTopicTreePort(),
            mkDocsConfigGateway = StubConfigGateway(),
            docsFileGateway = docsGateway,
        )

        val transaction = TopicSyncTransaction(
            transactionId = "tx-1",
            instance = TopicInstanceRef("default", "mkdocs.yml", "docs"),
            command = AddChildTopicNodeCommand(
                commandId = "cmd-1",
                treeId = "tree-1",
                targetNodeId = "root",
                childNodeId = "child-1",
                childTitle = "Child",
                childOrderIndex = 0,
                childSourcePath = "child.md"
            )
        )

        val result = orchestrator.apply(transaction)
        val outcome = (result as TopicGatewayResult.Success).value

        // Verify that file operations were generated and executed
        assertTrue(docsGateway.ops.contains("create:child.md"), "Expected file creation op but got: ${docsGateway.ops}")
    }

    @Test
    fun `apply generates file creation ops for AddTopicNodeCommand with null sourcePath (Derivation)`() {
        val docsGateway = StubDocsFileGateway()
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = StubTopicTreePort(),
            mkDocsConfigGateway = StubConfigGateway(),
            docsFileGateway = docsGateway,
        )

        val transaction = TopicSyncTransaction(
            transactionId = "tx-2",
            instance = TopicInstanceRef("default", "mkdocs.yml", "docs"),
            command = AddTopicNodeCommand(
                commandId = "cmd-2",
                treeId = "tree-1",
                parentNodeId = "root",
                nodeId = "new-node",
                title = "new",
                orderIndex = 0,
                sourcePath = null // Trigger derivation
            )
        )

        val result = orchestrator.apply(transaction)
        val outcome = (result as TopicGatewayResult.Success).value

        // Verify that file operations were generated and executed for "new.md"
        assertTrue(docsGateway.ops.contains("create:new.md"), "Expected derived file creation op 'create:new.md' but got: ${docsGateway.ops}")
    }

    class StubTopicTreePort : TopicTreePort {
        override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
            return TopicTreeCommandResult("cmd-1", TopicTreeCommandStatus.SUCCESS, 1)
        }
    }

    class StubConfigGateway : MkDocsConfigGateway {
        override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
            // Return valid config with root
            return TopicGatewayResult.Success(
                MkDocsConfigDocument("docs", listOf(TopicNavNode("root", "Root", children=listOf())))
            )
        }
        override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> = TopicGatewayResult.Success(Unit)
        override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> = TopicGatewayResult.Success("")
    }

    class StubDocsFileGateway : DocsFileGateway {
        val ops = mutableListOf<String>()
        override fun createMarkdownFile(instance: TopicInstanceRef, relativePath: String, initialContent: String): TopicGatewayResult<String> {
            ops.add("create:$relativePath")
            return TopicGatewayResult.Success(relativePath)
        }
        override fun deleteMarkdownFile(instance: TopicInstanceRef, relativePath: String, mode: TopicDeleteMode): TopicGatewayResult<String> = TopicGatewayResult.Success("")
        override fun renameMarkdownFile(instance: TopicInstanceRef, fromRelativePath: String, toRelativePath: String): TopicGatewayResult<String> = TopicGatewayResult.Success("")
        override fun moveMarkdownFile(instance: TopicInstanceRef, fromRelativePath: String, toRelativePath: String): TopicGatewayResult<String> = TopicGatewayResult.Success("")
        override fun rewriteRelativeMarkdownLinks(instance: TopicInstanceRef, fromRelativePath: String, toRelativePath: String): TopicGatewayResult<Int> = TopicGatewayResult.Success(0)
    }
}
