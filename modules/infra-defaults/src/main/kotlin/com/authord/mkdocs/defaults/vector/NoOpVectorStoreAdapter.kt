package com.authord.mkdocs.defaults.vector

import com.authord.mkdocs.ports.vector.VectorStorePort

/**
 * MVP default vector-store adapter with no persisted behavior.
 */
class NoOpVectorStoreAdapter : VectorStorePort {
    /** Intentionally ignored in MVP. */
    override fun upsert(documentId: String, content: String) {
        // Intentionally no-op in MVP.
    }

    /** Always returns empty in MVP default adapter. */
    override fun search(query: String, limit: Int): List<String> = emptyList()
}
