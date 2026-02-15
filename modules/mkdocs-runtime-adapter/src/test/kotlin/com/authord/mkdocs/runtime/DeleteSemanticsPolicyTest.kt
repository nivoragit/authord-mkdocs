package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DeleteSemanticsPolicyTest {
    @Test
    fun `recoverable delete moves markdown file into recovery location`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter()

        val recoveryPath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(filePath))
        assertTrue(recoveryPath.startsWith(".recovery/"))
        assertTrue(Files.exists(docsDir.resolve(recoveryPath)))
    }

    @Test
    fun `nav-only delete leaves underlying markdown file untouched`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter()

        val returnedPath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/page.md", TopicDeleteMode.NAV_ONLY),
        )

        assertTrue(Files.exists(filePath))
        assertTrue(returnedPath.endsWith("guide/page.md"))
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}
