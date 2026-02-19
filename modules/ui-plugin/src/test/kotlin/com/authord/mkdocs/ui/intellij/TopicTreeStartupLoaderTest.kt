package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode
import java.nio.file.Files
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

    @Test
    fun `loads fallback tree when docs dir is absolute path`() {
        val loader = TopicTreeStartupLoader()
        val config = MkDocsConfigDocument(
            docsDir = "/tmp/project/docs",
            nav = emptyList(),
            navPresent = false,
        )

        val state = loader.load(
            config = config,
            docsMarkdownPaths = listOf(
                "/tmp/project/docs/index.md",
                "/tmp/project/docs/guide/install.md",
            ),
        )

        assertEquals(StartupTreeSource.FALLBACK, state.source)
        assertEquals(listOf("guide/install.md", "index.md"), state.navOrderedPaths)
    }

    @Test
    fun `resolves nav display title from markdown h1 when present`() {
        val root = Files.createTempDirectory("startup-loader-h1-nav")
        val docs = Files.createDirectories(root.resolve("docs"))
        val page = docs.resolve("index.md")
        Files.writeString(page, "# Welcome Home\n\nBody\n")

        val loader = TopicTreeStartupLoader()
        val config = MkDocsConfigDocument(
            docsDir = docs.toString(),
            nav = listOf(TopicNavNode(nodeId = "n1", title = "Fallback Title", path = "index.md")),
        )

        val state = loader.load(
            config = config,
            docsMarkdownPaths = listOf(page.toString()),
        )

        assertEquals("Welcome Home", state.nodes.single().title)
    }

    @Test
    fun `reloading startup state reflects updated markdown h1 title`() {
        val root = Files.createTempDirectory("startup-loader-h1-refresh")
        val docs = Files.createDirectories(root.resolve("docs"))
        val page = docs.resolve("guide.md")
        Files.writeString(page, "# First Title\n")

        val loader = TopicTreeStartupLoader()
        val config = MkDocsConfigDocument(
            docsDir = docs.toString(),
            nav = listOf(TopicNavNode(nodeId = "n1", title = "Config Title", path = "guide.md")),
        )

        val first = loader.load(config = config, docsMarkdownPaths = listOf(page.toString()))
        assertEquals("First Title", first.nodes.single().title)

        Files.writeString(page, "# Updated Title\n")
        val second = loader.load(config = config, docsMarkdownPaths = listOf(page.toString()))
        assertEquals("Updated Title", second.nodes.single().title)
    }
}
