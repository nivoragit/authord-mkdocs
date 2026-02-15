package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreeStartupLoaderTest {
    @Test
    fun `loads startup tree from canonical nav when nav exists`() {
        val loader = TopicTreeStartupLoader()
        val config = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(nodeId = "n1", title = "Intro", path = "index.md"),
                TopicNavNode(
                    nodeId = "n2",
                    title = "Guide",
                    children = listOf(
                        TopicNavNode(nodeId = "n3", title = "Install", path = "guide/install.md"),
                    ),
                ),
            ),
        )

        val state = loader.load(
            config = config,
            docsMarkdownPaths = listOf(
                "docs/index.md",
                "docs/guide/install.md",
                "docs/guide/extra.md",
            ),
        )

        assertEquals(StartupTreeSource.NAV, state.source)
        assertEquals(listOf("Intro", "Guide"), state.nodes.map { it.title })
        assertEquals(listOf("index.md", "guide/install.md"), state.navOrderedPaths)
        assertEquals(listOf("docs/guide/extra.md"), state.unlinkedPaths)
        assertTrue(state.validationIssues.isEmpty())
    }

    @Test
    fun `preserves nav ordering for startup tree`() {
        val loader = TopicTreeStartupLoader()
        val config = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(nodeId = "n2", title = "Guide", path = "guide.md"),
                TopicNavNode(nodeId = "n1", title = "Intro", path = "index.md"),
            ),
        )

        val state = loader.load(
            config = config,
            docsMarkdownPaths = listOf("docs/index.md", "docs/guide.md"),
        )

        assertEquals(StartupTreeSource.NAV, state.source)
        assertEquals(listOf("n2", "n1"), state.nodes.map { it.nodeId })
        assertEquals(listOf("guide.md", "index.md"), state.navOrderedPaths)
    }
}
