package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TreeSyncOrchestratorAtomicityTest {
    @Test
    fun `apply succeeds when command config and file operations all succeed`() {
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = SuccessfulTopicTreePort(),
            mkDocsConfigGateway = SuccessfulConfigGateway(),
            docsFileGateway = RecordingDocsFileGateway(),
        )

        val transaction = transaction(
            fileOperations = listOf(
                TopicFileOperation(TopicFileOperationKind.CREATE, "guide/new.md"),
            ),
        )

        val outcome = requireSuccess(orchestrator.apply(transaction))

        assertTrue(outcome.applied)
        assertFalse(outcome.rolledBack)
        assertFalse(outcome.compensated)
    }

    @Test
    fun `apply rolls back with compensation when a later file operation fails`() {
        val docsGateway = RecordingDocsFileGateway(failOnMove = true)
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = SuccessfulTopicTreePort(),
            mkDocsConfigGateway = SuccessfulConfigGateway(),
            docsFileGateway = docsGateway,
        )

        val transaction = transaction(
            fileOperations = listOf(
                TopicFileOperation(TopicFileOperationKind.CREATE, "guide/new.md"),
                TopicFileOperation(TopicFileOperationKind.MOVE, "guide/new.md", "archive/new.md"),
            ),
        )

        val outcome = requireSuccess(orchestrator.apply(transaction))

        assertFalse(outcome.applied)
        assertTrue(outcome.rolledBack)
        assertTrue(outcome.compensated)
        assertEquals(
            listOf(
                "create:guide/new.md",
                "move:guide/new.md->archive/new.md",
                "delete:guide/new.md:RECOVERABLE",
            ),
            docsGateway.calls,
        )
    }

    private fun transaction(fileOperations: List<TopicFileOperation>): TopicSyncTransaction {
        return TopicSyncTransaction(
            transactionId = "tx-1",
            instance = TopicInstanceRef("default", "/project/mkdocs.yml", "/project/docs"),
            command = ValidateTopicTreeCommand("cmd-1", "tree-1"),
            fileOperations = fileOperations,
        )
    }

    private fun requireSuccess(result: TopicGatewayResult<com.authord.mkdocs.ports.topic.TopicSyncOutcome>): com.authord.mkdocs.ports.topic.TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class SuccessfulTopicTreePort : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            violations = emptyList(),
            message = "ok",
        )
    }
}

private class SuccessfulConfigGateway : MkDocsConfigGateway {
    private val document = MkDocsConfigDocument(docsDir = "docs", nav = emptyList(), notInNav = emptyList())

    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Success(document)
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        return TopicGatewayResult.Success(Unit)
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Success("docs_dir: docs\nnav: []\n")
    }
}

private class RecordingDocsFileGateway(
    private val failOnMove: Boolean = false,
) : DocsFileGateway {
    val calls = mutableListOf<String>()

    override fun createMarkdownFile(instance: TopicInstanceRef, relativePath: String, initialContent: String): TopicGatewayResult<String> {
        calls += "create:$relativePath"
        return TopicGatewayResult.Success(relativePath)
    }

    override fun deleteMarkdownFile(instance: TopicInstanceRef, relativePath: String, mode: TopicDeleteMode): TopicGatewayResult<String> {
        calls += "delete:$relativePath:$mode"
        return TopicGatewayResult.Success(relativePath)
    }

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        calls += "rename:$fromRelativePath->$toRelativePath"
        return TopicGatewayResult.Success(toRelativePath)
    }

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        calls += "move:$fromRelativePath->$toRelativePath"
        return if (failOnMove) {
            TopicGatewayResult.Failure(DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "move failed"))
        } else {
            TopicGatewayResult.Success(toRelativePath)
        }
    }

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        calls += "rewrite:$fromRelativePath->$toRelativePath"
        return TopicGatewayResult.Success(1)
    }
}
