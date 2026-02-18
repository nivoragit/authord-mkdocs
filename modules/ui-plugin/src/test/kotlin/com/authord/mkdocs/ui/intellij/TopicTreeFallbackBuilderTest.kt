package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals

class TopicTreeFallbackBuilderTest {
    @Test
    fun `builds deterministic fallback tree from docs dir markdown paths`() {
        val builder = TopicTreeFallbackBuilder()
        val docs = listOf(
            "docs/guide/install.md",
            "docs/index.md",
            "docs/guide/advanced.md",
        )

        val first = builder.build(docsDir = "docs", docsMarkdownPaths = docs)
        val second = builder.build(docsDir = "docs", docsMarkdownPaths = docs.shuffled())

        assertEquals(first, second)
    }

    @Test
    fun `creates section hierarchy when fallback paths contain directories`() {
        val builder = TopicTreeFallbackBuilder()

        val tree = builder.build(
            docsDir = "docs",
            docsMarkdownPaths = listOf(
                "docs/guide/install.md",
                "docs/guide/advanced.md",
                "docs/index.md",
            ),
        )

        assertEquals(listOf("Guide", "Index"), tree.map { it.title })

        val guide = tree.first { it.title == "Guide" }
        assertEquals(listOf("guide/advanced.md", "guide/install.md"), guide.children.map { it.path })
    }

    @Test
    fun `supports absolute docs dir with absolute markdown paths`() {
        val builder = TopicTreeFallbackBuilder()
        val tree = builder.build(
            docsDir = "/tmp/project/docs",
            docsMarkdownPaths = listOf(
                "/tmp/project/docs/index.md",
                "/tmp/project/docs/guide/install.md",
            ),
        )

        assertEquals(listOf("Guide", "Index"), tree.map { it.title })
        assertEquals(listOf("guide/install.md", "index.md"), tree.flatMap { node ->
            if (node.path != null) listOf(node.path!!) else node.children.mapNotNull { it.path }
        })
    }
}
