package com.authord.mkdocs.ports.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsFileGatewayContractTest {
    @Test
    fun `defines markdown create delete rename move rewrite and heading sync operations`() {
        val methods = DocsFileGateway::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("createMarkdownFile"))
        assertTrue(methods.contains("deleteMarkdownFile"))
        assertTrue(methods.contains("renameMarkdownFile"))
        assertTrue(methods.contains("moveMarkdownFile"))
        assertTrue(methods.contains("rewriteRelativeMarkdownLinks"))
        assertTrue(methods.contains("upsertMarkdownTitleHeading"))
    }

    @Test
    fun `delete mode supports recoverable and nav only variants`() {
        val modes = TopicDeleteMode.entries.map { it.name }.toSet()
        assertEquals(setOf("RECOVERABLE", "NAV_ONLY"), modes)
    }
}
