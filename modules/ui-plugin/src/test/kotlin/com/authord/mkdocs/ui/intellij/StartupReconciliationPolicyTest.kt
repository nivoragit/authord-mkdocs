package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.TopicTreeValidationIssueType
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupReconciliationPolicyTest {
    @Test
    fun `applies nav-first reconciliation with non-destructive policy`() {
        val coordinator = StartupReconciliationCoordinator()
        val config = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(nodeId = "n1", title = "Intro", path = "index.md"),
                TopicNavNode(nodeId = "n2", title = "Missing", path = "missing.md"),
            ),
        )

        val result = coordinator.reconcile(
            config = config,
            docsMarkdownPaths = listOf("docs/index.md", "docs/extra.md"),
        )

        assertEquals(StartupTreeSource.NAV, result.source)
        assertEquals(listOf("n1", "n2"), result.nodes.map { it.nodeId })
        assertEquals(listOf("docs/extra.md"), result.unlinkedPaths)
        assertTrue(result.validationIssues.any { it.type == TopicTreeValidationIssueType.BROKEN_PATH })
        assertFalse(result.destructiveChangesApplied)
    }

    @Test
    fun `keeps fallback mode deterministic when nav is missing`() {
        val coordinator = StartupReconciliationCoordinator()

        val first = coordinator.reconcile(
            config = MkDocsConfigDocument(docsDir = "docs", nav = emptyList()),
            docsMarkdownPaths = listOf("docs/guide.md", "docs/index.md"),
        )
        val second = coordinator.reconcile(
            config = MkDocsConfigDocument(docsDir = "docs", nav = emptyList()),
            docsMarkdownPaths = listOf("docs/index.md", "docs/guide.md"),
        )

        assertEquals(StartupTreeSource.FALLBACK, first.source)
        assertEquals(first.nodes, second.nodes)
    }
}
