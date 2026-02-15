package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MarkdownLinkRewriteScopeTest {
    @Test
    fun `rewrite updates only markdown relative links and preserves external or non-markdown targets`() {
        val projectRoot = Files.createTempDirectory("rewrite-scope")
        val docsDir = Files.createDirectories(projectRoot.resolve("docs"))
        Files.createDirectories(docsDir.resolve("guide"))
        Files.createDirectories(docsDir.resolve("notes"))

        Files.writeString(docsDir.resolve("guide/intro.md"), "# intro")
        Files.writeString(
            docsDir.resolve("index.md"),
            """
            [Guide](guide/intro.md)
            [External](https://example.com/docs)
            [Asset](assets/logo.png)
            """.trimIndent() + "\n",
        )
        Files.writeString(
            docsDir.resolve("notes/other.md"),
            """
            [Guide From Subdir](../guide/intro.md#start)
            [Anchor](#local)
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef("default", projectRoot.resolve("mkdocs.yml").toString(), docsDir.toString())
        val gateway = DocsFileGatewayAdapter()

        val rewriteCount = requireSuccess(
            gateway.rewriteRelativeMarkdownLinks(instance, "guide/intro.md", "guide/renamed.md"),
        )

        assertEquals(2, rewriteCount)
        val index = Files.readString(docsDir.resolve("index.md"))
        val other = Files.readString(docsDir.resolve("notes/other.md"))

        assertTrue(index.contains("[Guide](guide/renamed.md)"))
        assertTrue(index.contains("[External](https://example.com/docs)"))
        assertTrue(index.contains("[Asset](assets/logo.png)"))

        assertTrue(other.contains("[Guide From Subdir](../guide/renamed.md#start)"))
        assertTrue(other.contains("[Anchor](#local)"))
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}
