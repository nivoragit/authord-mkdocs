package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class DocsFileGatewayAdapterMutationTest {
    @Test
    fun `gateway synchronizes create rename move and recoverable delete operations`() {
        val projectRoot = Files.createTempDirectory("docs-gateway")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = projectRoot.resolve("mkdocs.yml").toString(),
            docsDirPath = docsDir.toString(),
        )

        val gateway = DocsFileGatewayAdapter(trashMover = { false })

        val created = requireSuccess(gateway.createMarkdownFile(instance, "guide/new.md", "# new"))
        assertEquals("guide/new.md", created)
        assertTrue(Files.exists(docsDir.resolve("guide/new.md")))

        val renamed = requireSuccess(gateway.renameMarkdownFile(instance, "guide/new.md", "guide/renamed.md"))
        assertEquals("guide/renamed.md", renamed)
        assertFalse(Files.exists(docsDir.resolve("guide/new.md")))
        assertTrue(Files.exists(docsDir.resolve("guide/renamed.md")))

        val moved = requireSuccess(gateway.moveMarkdownFile(instance, "guide/renamed.md", "archive/renamed.md"))
        assertEquals("archive/renamed.md", moved)
        assertFalse(Files.exists(docsDir.resolve("guide/renamed.md")))
        assertTrue(Files.exists(docsDir.resolve("archive/renamed.md")))

        val recoveryPath = requireSuccess(gateway.deleteMarkdownFile(instance, "archive/renamed.md", TopicDeleteMode.RECOVERABLE))
        assertFalse(Files.exists(docsDir.resolve("archive/renamed.md")))
        assertTrue(recoveryPath.startsWith(".recovery/"))
        assertTrue(Files.exists(docsDir.resolve(recoveryPath)))
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}
