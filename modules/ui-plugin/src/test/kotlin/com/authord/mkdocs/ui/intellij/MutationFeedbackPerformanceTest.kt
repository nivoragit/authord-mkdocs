package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class MutationFeedbackPerformanceTest {
    @Test
    fun `mutation feedback stays within 300ms p95 on local orchestration`() {
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = FastTopicTreePort(),
            mkDocsConfigGateway = FastConfigGateway(),
            docsFileGateway = FastDocsFileGateway(),
        )
        val instance = TopicInstanceRef("default", "/project/mkdocs.yml", "/project/docs")

        val durationsMs = (1..120).map { index ->
            val transaction = TopicSyncTransaction(
                transactionId = "tx-$index",
                instance = instance,
                command = ValidateTopicTreeCommand(commandId = "cmd-$index", treeId = "tree-latency"),
                fileOperations = emptyList(),
            )

            measureNanoTime {
                val result = orchestrator.apply(transaction)
                assertTrue(result is TopicGatewayResult.Success)
            } / 1_000_000.0
        }.sorted()

        val p95Index = ((durationsMs.size - 1) * 0.95).toInt()
        val p95 = durationsMs[p95Index]
        assertTrue(p95 <= 300.0, "Expected p95 <= 300ms, got ${"%.2f".format(p95)}ms")
    }
}

private class FastTopicTreePort : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(command.commandId, TopicTreeCommandStatus.SUCCESS, treeVersion = 1, message = "ok")
    }
}

private class FastConfigGateway : MkDocsConfigGateway {
    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Success(MkDocsConfigDocument(docsDir = "docs", nav = emptyList()))
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        return TopicGatewayResult.Success(Unit)
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Success("docs_dir: docs\nnav: []\n")
    }
}

private class FastDocsFileGateway : DocsFileGateway {
    override fun createMarkdownFile(instance: TopicInstanceRef, relativePath: String, initialContent: String): TopicGatewayResult<String> {
        return TopicGatewayResult.Success(relativePath)
    }

    override fun deleteMarkdownFile(instance: TopicInstanceRef, relativePath: String, mode: TopicDeleteMode): TopicGatewayResult<String> {
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
