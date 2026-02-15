package com.authord.mkdocs.ports.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MkDocsConfigGatewayContractTest {
    @Test
    fun `defines deterministic config load write and serialize contract`() {
        val methods = MkDocsConfigGateway::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("loadConfig"))
        assertTrue(methods.contains("writeConfig"))
        assertTrue(methods.contains("serializeDeterministically"))
    }

    @Test
    fun `config document keeps nav and not in nav collections`() {
        val document = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(TopicNavNode(nodeId = "n1", title = "Intro", path = "index.md")),
            notInNav = listOf("index2.md"),
        )

        assertEquals("docs", document.docsDir)
        assertEquals(1, document.nav.size)
        assertEquals(1, document.notInNav.size)
    }
}
