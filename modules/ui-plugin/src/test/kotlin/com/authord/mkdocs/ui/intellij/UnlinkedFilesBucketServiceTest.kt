package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals

class UnlinkedFilesBucketServiceTest {
    @Test
    fun `derives docs_dir markdown files not present in nav`() {
        val service = UnlinkedFilesBucketService()
        val nav = listOf(
            TopicNavNode(nodeId = "n1", title = "Intro", path = "index.md"),
            TopicNavNode(nodeId = "n2", title = "Install", path = "guide/install.md"),
        )

        val unlinked = service.derive(
            docsDir = "docs",
            docsMarkdownPaths = listOf(
                "docs/index.md",
                "docs/guide/install.md",
                "docs/guide/extra.md",
                "docs/faq.md",
            ),
            navNodes = nav,
        )

        assertEquals(listOf("docs/faq.md", "docs/guide/extra.md"), unlinked)
    }

    @Test
    fun `normalizes docs paths when nav paths use relative separators`() {
        val service = UnlinkedFilesBucketService()
        val nav = listOf(
            TopicNavNode(nodeId = "n1", title = "Windows", path = "guide/install.md"),
        )

        val unlinked = service.derive(
            docsDir = "docs",
            docsMarkdownPaths = listOf("docs\\guide\\install.md", "docs\\guide\\extra.md"),
            navNodes = nav,
        )

        assertEquals(listOf("docs/guide/extra.md"), unlinked)
    }
}
