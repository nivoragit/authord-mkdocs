package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class TopicTreeSyncOrchestratorCacheFreshnessTest {
    @Test
    fun `nav-present cache reloads after external config file change`() {
        val root = Files.createTempDirectory("orchestrator-freshness")
        val configPath = root.resolve("mkdocs.yml")
        val docsDir = Files.createDirectories(root.resolve("docs"))
        try {
            Files.writeString(
                configPath,
                """
                docs_dir: docs
                nav:
                  - Legacy: legacy.md
                """.trimIndent() + "\n",
            )
            val instance = TopicInstanceRef(
                instanceId = "default",
                configPath = configPath.toString(),
                docsDirPath = docsDir.toString(),
            )
            val orchestrator = TopicTreeSyncOrchestratorService(
                topicTreePort = AlwaysSuccessTopicTreePort(),
                mkDocsConfigGateway = MkDocsYamlGateway(),
                docsFileGateway = NoopDocsGateway(),
            )

            requireSuccess(
                orchestrator.apply(
                    TopicSyncTransaction(
                        transactionId = "tx-prime-cache",
                        instance = instance,
                        command = ValidateTopicTreeCommand("cmd-prime-cache", "tree"),
                    ),
                ),
            )

            Thread.sleep(20L)
            Files.writeString(
                configPath,
                """
                docs_dir: docs
                nav:
                  - Updated: updated.md
                """.trimIndent() + "\n",
            )

            val outcome = requireSuccess(
                orchestrator.apply(
                    TopicSyncTransaction(
                        transactionId = "tx-freshness-reload",
                        instance = instance,
                        command = AddChildTopicNodeCommand(
                            commandId = "cmd-freshness-reload",
                            treeId = "tree",
                            targetNodeId = "page:updated.md",
                            childNodeId = "child",
                            childTitle = "Child",
                            childOrderIndex = 1,
                            childSourcePath = "updated/child.md",
                        ),
                    ),
                ),
            )

            assertTrue(outcome.applied)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun requireSuccess(result: TopicGatewayResult<TopicSyncOutcome>): TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class AlwaysSuccessTopicTreePort : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            message = "Applied",
        )
    }
}

private class NoopDocsGateway : DocsFileGateway {
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
