package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class StartupReconciliationPerformanceTest {
    @Test
    fun `startup reconciliation meets p95 threshold for reference dataset`() {
        val coordinator = StartupReconciliationCoordinator()
        val docs = (1..1_000).map { "docs/topic-$it.md" }
        val nav = docs.mapIndexed { index, path ->
            TopicNavNode(
                nodeId = "n$index",
                title = "Topic $index",
                path = path.removePrefix("docs/"),
            )
        }
        val config = MkDocsConfigDocument(docsDir = "docs", nav = nav)

        val samples = mutableListOf<Long>()
        repeat(20) {
            val elapsed = measureTimeMillis {
                coordinator.reconcile(config = config, docsMarkdownPaths = docs)
            }
            samples += elapsed
        }

        val sorted = samples.sorted()
        val p95Index = ((sorted.size - 1) * 0.95).toInt()
        val p95 = sorted[p95Index]

        assertTrue(p95 <= 2_000, "Expected p95 <= 2000ms but was ${p95}ms")
    }
}
