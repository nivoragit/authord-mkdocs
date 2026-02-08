package com.authord.mkdocs.ports.vector

/** API version for vector-store seam. */
const val VECTOR_STORE_PORT_API_VERSION: String = "1.0.0"

/**
 * Seam contract for vector store integration.
 *
 * API Version: [VECTOR_STORE_PORT_API_VERSION]
 *
 * Constraints:
 * - MVP default adapters may persist nothing and return empty results.
 * - Future adapters may provide semantic/vector retrieval behavior.
 */
interface VectorStorePort {
    /** Upserts content for a document key. */
    fun upsert(documentId: String, content: String)

    /**
     * Searches vector-backed content and returns provider-specific IDs/snippets.
     */
    fun search(query: String, limit: Int): List<String>
}
