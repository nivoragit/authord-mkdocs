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
    fun `recoverable delete with default trash strategy removes markdown safely`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter()

        val deletePath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(filePath))
        if (deletePath.startsWith(".recovery/")) {
            assertTrue(Files.exists(docsDir.resolve(deletePath)))
        } else {
            assertTrue(deletePath.endsWith("guide/page.md"))
        }
    }

    @Test
    fun `recoverable delete uses system trash path when available`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter(
            trashMover = { path ->
                Files.delete(path)
                true
            },
        )

        val deletedPath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(filePath))
        assertTrue(deletedPath.endsWith("guide/page.md"))
    }

    @Test
    fun `recoverable delete falls back to recovery location when system trash unavailable`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter(trashMover = { false })

        val recoveryPath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(filePath))
        assertTrue(recoveryPath.startsWith(".recovery/"))
        assertTrue(Files.exists(docsDir.resolve(recoveryPath)))
    }

    @Test
    fun `recoverable delete prunes empty branch directories`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/install/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter(trashMover = { false })

        val recoveryPath = requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/install/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(filePath))
        assertFalse(Files.exists(docsDir.resolve("guide/install")))
        assertFalse(Files.exists(docsDir.resolve("guide")))
        assertTrue(recoveryPath.startsWith(".recovery/"))
    }

    @Test
    fun `recoverable delete keeps non-empty ancestor directories`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val deletedPath = docsDir.resolve("guide/install/page.md")
        val siblingPath = docsDir.resolve("guide/overview.md")
        Files.createDirectories(deletedPath.parent)
        Files.writeString(deletedPath, "# page")
        Files.writeString(siblingPath, "# overview")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter(trashMover = { false })

        requireSuccess(
            gateway.deleteMarkdownFile(instance, "guide/install/page.md", TopicDeleteMode.RECOVERABLE),
        )

        assertFalse(Files.exists(deletedPath))
        assertFalse(Files.exists(docsDir.resolve("guide/install")))
        assertTrue(Files.exists(docsDir.resolve("guide")))
        assertTrue(Files.exists(siblingPath))
    }

    @Test
    fun `nav-only delete leaves underlying markdown file untouched`() {
        val projectRoot = Files.createTempDirectory("delete-policy")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val filePath = docsDir.resolve("guide/page.md")
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "# page")

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter(trashMover = { false })

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
