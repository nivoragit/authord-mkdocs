package com.authord.mkdocs.defaults.ports

import com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter
import com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoOpAdaptersTest {
    @Test
    fun `preview sync adapter captures deltas without side effects`() {
        val adapter = NoOpPreviewSyncAdapter()

        adapter.onEditorScrollSemanticDelta("project-1", "docs/index.md", 15)

        assertEquals(listOf(15), adapter.receivedDeltas)
    }

    @Test
    fun `vector store adapter returns empty results`() {
        val adapter = NoOpVectorStoreAdapter()

        adapter.upsert("doc-1", "content")
        val result = adapter.search("query", 5)

        assertTrue(result.isEmpty())
    }
}
