package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiInstanceReconciliationTest {
    @Test
    fun `reconciliation runs per instance with nav-first isolation`() {
        val coordinator = MultiInstanceReconciliationCoordinator()
        val inputA = InstanceReconciliationInput(
            instance = TopicInstanceRef(
                instanceId = "instance-a",
                configPath = "/project/a/mkdocs.yml",
                docsDirPath = "/project/a/docs",
            ),
            config = MkDocsConfigDocument(
                docsDir = "/project/a/docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "a-guide",
                        title = "Guide",
                        path = "guide.md",
                    ),
                ),
            ),
            docsMarkdownPaths = listOf(
                "/project/a/docs/guide.md",
                "/project/a/docs/extra.md",
            ),
        )
        val inputB = InstanceReconciliationInput(
            instance = TopicInstanceRef(
                instanceId = "instance-b",
                configPath = "/project/b/mkdocs.yml",
                docsDirPath = "/project/b/docs",
            ),
            config = MkDocsConfigDocument(
                docsDir = "/project/b/docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "b-overview",
                        title = "Overview",
                        path = "overview.md",
                    ),
                ),
            ),
            docsMarkdownPaths = listOf("/project/b/docs/overview.md"),
        )

        val result = coordinator.reconcile(
            projectId = "project-x",
            inputs = listOf(inputA, inputB),
        )

        assertEquals(setOf("instance-a", "instance-b"), result.statesByInstanceId.keys)
        val stateA = result.statesByInstanceId.getValue("instance-a")
        val stateB = result.statesByInstanceId.getValue("instance-b")
        assertEquals(listOf("guide.md"), stateA.navOrderedPaths)
        assertEquals(listOf("overview.md"), stateB.navOrderedPaths)
        assertEquals(listOf("/project/a/docs/extra.md"), stateA.unlinkedPaths)
        assertTrue(stateB.unlinkedPaths.isEmpty())
    }

    @Test
    fun `missing nav file remains validation issue without destructive changes`() {
        val coordinator = MultiInstanceReconciliationCoordinator()
        val input = InstanceReconciliationInput(
            instance = TopicInstanceRef(
                instanceId = "instance-c",
                configPath = "/project/c/mkdocs.yml",
                docsDirPath = "/project/c/docs",
            ),
            config = MkDocsConfigDocument(
                docsDir = "/project/c/docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "missing",
                        title = "Missing",
                        path = "missing.md",
                    ),
                ),
            ),
            docsMarkdownPaths = listOf("/project/c/docs/existing.md"),
        )

        val result = coordinator.reconcile(
            projectId = "project-x",
            inputs = listOf(input),
        )

        val state = result.statesByInstanceId.getValue("instance-c")
        assertTrue(state.validationIssues.isNotEmpty())
        assertEquals(false, state.destructiveChangesApplied)
    }
}
