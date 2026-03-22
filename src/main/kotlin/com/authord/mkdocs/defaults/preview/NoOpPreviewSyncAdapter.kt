package com.authord.mkdocs.defaults.preview

import com.authord.mkdocs.ports.preview.PreviewSyncPort

/**
 * MVP default preview-sync adapter that records deltas without side effects.
 */
class NoOpPreviewSyncAdapter : PreviewSyncPort {
    val receivedDeltas: MutableList<Int> = mutableListOf()

    /**
     * Records delta for observability/testing while intentionally performing no sync behavior.
     */
    override fun onEditorScrollSemanticDelta(projectId: String, documentPath: String, delta: Int) {
        receivedDeltas += delta
    }
}
