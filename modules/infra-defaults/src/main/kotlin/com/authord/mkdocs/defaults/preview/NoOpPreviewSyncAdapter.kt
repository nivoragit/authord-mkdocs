package com.authord.mkdocs.defaults.preview

import com.authord.mkdocs.ports.preview.PreviewSyncPort

class NoOpPreviewSyncAdapter : PreviewSyncPort {
    val receivedDeltas: MutableList<Int> = mutableListOf()

    override fun onEditorScrollSemanticDelta(projectId: String, documentPath: String, delta: Int) {
        receivedDeltas += delta
    }
}
