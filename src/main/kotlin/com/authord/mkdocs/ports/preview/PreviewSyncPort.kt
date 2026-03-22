package com.authord.mkdocs.ports.preview

/** API version for preview-sync seam. */
const val PREVIEW_SYNC_PORT_API_VERSION: String = "1.0.0"

/**
 * Seam contract for preview synchronization features.
 *
 * API Version: [PREVIEW_SYNC_PORT_API_VERSION]
 *
 * Constraints:
 * - MVP implementations may no-op intentionally.
 * - Callers provide semantic delta values already excluding comment ranges.
 */
interface PreviewSyncPort {
    /**
     * Receives semantic editor scroll delta events for a project document.
     */
    fun onEditorScrollSemanticDelta(projectId: String, documentPath: String, delta: Int)
}
