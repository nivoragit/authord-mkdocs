package com.authord.mkdocs.defaults.vector

import com.authord.mkdocs.ports.vector.VectorStorePort

class NoOpVectorStoreAdapter : VectorStorePort {
    override fun upsert(documentId: String, content: String) {
        // Intentionally no-op in MVP.
    }

    override fun search(query: String, limit: Int): List<String> = emptyList()
}
