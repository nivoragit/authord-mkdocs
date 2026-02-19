package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownHeadingSupportTest {
    @Test
    fun `extract first h1 skips code fences and returns first top-level heading`() {
        val markdown = """
            ```md
            # not this one
            ```
            
            # Actual Title
            
            ## Section
        """.trimIndent()

        assertEquals("Actual Title", MarkdownHeadingSupport.extractFirstH1(markdown))
    }

    @Test
    fun `extract first h1 handles yaml front matter`() {
        val markdown = """
            ---
            title: Metadata title
            ---
            
            # Page Heading
        """.trimIndent()

        assertEquals("Page Heading", MarkdownHeadingSupport.extractFirstH1(markdown))
    }

    @Test
    fun `extract first h1 returns null when heading is missing`() {
        assertNull(MarkdownHeadingSupport.extractFirstH1("## Section only"))
    }

    @Test
    fun `upsert first h1 replaces existing heading`() {
        val markdown = """
            # Old Title
            
            Body
        """.trimIndent()

        val updated = MarkdownHeadingSupport.upsertFirstH1(markdown, "New Title")

        assertTrue(updated.startsWith("# New Title"))
        assertEquals("New Title", MarkdownHeadingSupport.extractFirstH1(updated))
    }

    @Test
    fun `upsert first h1 inserts heading after front matter when absent`() {
        val markdown = """
            ---
            tags: [a, b]
            ---
            
            Body
        """.trimIndent()

        val updated = MarkdownHeadingSupport.upsertFirstH1(markdown, "Injected")
        val expectedPrefix = """
            ---
            tags: [a, b]
            ---
            
            # Injected
        """.trimIndent()

        assertTrue(updated.startsWith(expectedPrefix))
    }
}
