package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstanceScaleProfileIntegrationTest {
    @Test
    fun `reconciles five instances and ten thousand docs without cross-instance leakage`() {
        val coordinator = MultiInstanceReconciliationCoordinator()
        val instanceCount = 5
        val docsPerInstance = 2_000
        val inputs = (1..instanceCount).map { instanceNumber ->
            val instanceId = "instance-$instanceNumber"
            val docsRoot = "/scale/$instanceId/docs"
            val firstNavPath = "topic-0001.md"
            InstanceReconciliationInput(
                instance = TopicInstanceRef(
                    instanceId = instanceId,
                    configPath = "/scale/$instanceId/mkdocs.yml",
                    docsDirPath = docsRoot,
                ),
                config = MkDocsConfigDocument(
                    docsDir = docsRoot,
                    nav = listOf(
                        TopicNavNode(
                            nodeId = "$instanceId-nav",
                            title = "Topic 0001",
                            path = firstNavPath,
                        ),
                    ),
                ),
                docsMarkdownPaths = (1..docsPerInstance).map { docNumber ->
                    "$docsRoot/topic-${docNumber.toString().padStart(4, '0')}.md"
                },
            )
        }

        val result = coordinator.reconcile(
            projectId = "scale-project",
            inputs = inputs,
        )

        assertEquals(instanceCount, result.statesByInstanceId.size)
        inputs.forEach { input ->
            val state = result.statesByInstanceId.getValue(input.instance.instanceId)
            assertEquals(listOf("topic-0001.md"), state.navOrderedPaths)
            assertEquals(docsPerInstance - 1, state.unlinkedPaths.size)
            assertTrue(state.unlinkedPaths.none { it.startsWith("instance-") })
        }
    }
}
