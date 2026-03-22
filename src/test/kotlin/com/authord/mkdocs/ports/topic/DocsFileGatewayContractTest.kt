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

    @Test
    fun `default heading sync operation returns success with same relative path`() {
        val gateway = object : DocsFileGateway {
            override fun createMarkdownFile(
                instance: TopicInstanceRef,
                relativePath: String,
                initialContent: String,
            ): TopicGatewayResult<String> = TopicGatewayResult.Success(relativePath)

            override fun deleteMarkdownFile(
                instance: TopicInstanceRef,
                relativePath: String,
                mode: TopicDeleteMode,
            ): TopicGatewayResult<String> = TopicGatewayResult.Success(relativePath)

            override fun renameMarkdownFile(
                instance: TopicInstanceRef,
                fromRelativePath: String,
                toRelativePath: String,
            ): TopicGatewayResult<String> = TopicGatewayResult.Success(toRelativePath)

            override fun moveMarkdownFile(
                instance: TopicInstanceRef,
                fromRelativePath: String,
                toRelativePath: String,
            ): TopicGatewayResult<String> = TopicGatewayResult.Success(toRelativePath)

            override fun rewriteRelativeMarkdownLinks(
                instance: TopicInstanceRef,
                fromRelativePath: String,
                toRelativePath: String,
            ): TopicGatewayResult<Int> = TopicGatewayResult.Success(0)
        }
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/repo/mkdocs.yml",
            docsDirPath = "/repo/docs",
        )

        val result = gateway.upsertMarkdownTitleHeading(
            instance = instance,
            relativePath = "guides/intro.md",
            title = "Intro",
        )

        when (result) {
            is TopicGatewayResult.Success -> assertEquals("guides/intro.md", result.value)
            is TopicGatewayResult.Failure -> error("Expected success but received: ${result.error.detail}")
        }
    }
}
