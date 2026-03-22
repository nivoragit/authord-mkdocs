package com.authord.mkdocs.ui

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsExplorerServiceTest {
    @Test
    fun `discovers markdown files under docs root only`() {
        val projectRoot = Files.createTempDirectory("docs-explorer-test")
        val docsRoot = projectRoot.resolve("docs").createDirectories()
        docsRoot.resolve("index.md").writeText("# Home")
        docsRoot.resolve("foo.md").writeText("# Foo")
        projectRoot.resolve("README.md").writeText("ignore")

        val service = DocsExplorerService()
        val files = service.discoverMarkdownFiles(projectRoot.toString())

        assertEquals(listOf("docs/foo.md", "docs/index.md"), files)
        assertTrue(files.none { it == "README.md" })
    }

    @Test
    fun `returns empty list when docs directory does not exist`() {
        val projectRoot = Files.createTempDirectory("docs-explorer-missing")
        val service = DocsExplorerService()

        val files = service.discoverMarkdownFiles(projectRoot.toString())

        assertTrue(files.isEmpty())
    }
}
