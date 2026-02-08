package com.authord.mkdocs.ports.preview

interface PreviewSyncPort {
    fun onEditorScrollSemanticDelta(projectId: String, documentPath: String, delta: Int)
}
