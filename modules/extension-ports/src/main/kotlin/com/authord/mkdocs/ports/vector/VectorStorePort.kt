package com.authord.mkdocs.ports.vector

interface VectorStorePort {
    fun upsert(documentId: String, content: String)
    fun search(query: String, limit: Int): List<String>
}
