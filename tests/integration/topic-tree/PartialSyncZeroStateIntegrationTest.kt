package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.runtime.DocsFileGatewayAdapter
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class PartialSyncZeroStateIntegrationTest {
    @Test
    fun `failed file operation triggers compensation and leaves no unresolved partial state`() {
        val projectRoot = Files.createTempDirectory("partial-sync")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val configPath = projectRoot.resolve("mkdocs.yml")
        Files.writeString(
            configPath,
            """
            docs_dir: docs
            nav:
              - Home: index.md
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = docsDir.toString(),
        )
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = SuccessfulTopicTreePortForIntegration(),
            mkDocsConfigGateway = MkDocsYamlGateway(),
            docsFileGateway = DocsFileGatewayAdapter(trashMover = { false }),
        )

        val transaction = TopicSyncTransaction(
            transactionId = "tx-partial-sync",
            instance = instance,
            command = ValidateTopicTreeCommand(commandId = "cmd-partial-sync", treeId = "tree-partial-sync"),
            fileOperations = listOf(
                TopicFileOperation(kind = TopicFileOperationKind.CREATE, sourcePath = "guide/new.md"),
                TopicFileOperation(kind = TopicFileOperationKind.MOVE, sourcePath = "guide/new.md", targetPath = "../../escape.md"),
            ),
        )

        val outcome = requireSuccess(orchestrator.apply(transaction))

        assertFalse(outcome.applied)
        assertTrue(outcome.rolledBack)
        assertTrue(outcome.compensated)
        assertFalse(Files.exists(docsDir.resolve("guide/new.md")))
        assertTrue(Files.exists(docsDir.resolve(".recovery")))
    }

    private fun requireSuccess(result: TopicGatewayResult<com.authord.mkdocs.ports.topic.TopicSyncOutcome>): com.authord.mkdocs.ports.topic.TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class SuccessfulTopicTreePortForIntegration : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            message = "ok",
        )
    }
}
