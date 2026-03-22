package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.fileEditor.TextEditorWithPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthordSplitEditorLayoutStateServiceTest {
    @Test
    fun `service defaults to editor and preview layout`() {
        val service = AuthordSplitEditorLayoutStateService()

        assertEquals(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, service.preferredLayout())
    }

    @Test
    fun `service stores and resolves preferred layout`() {
        val service = AuthordSplitEditorLayoutStateService()
        val layout = TextEditorWithPreview.Layout.entries.firstOrNull {
            it != TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
        } ?: TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW

        service.setPreferredLayout(layout)

        assertEquals(layout, service.preferredLayout())
        assertEquals(layout.name, service.getState().preferredLayoutName)
    }

    @Test
    fun `service falls back to default for invalid persisted layout`() {
        val service = AuthordSplitEditorLayoutStateService()
        service.loadState(AuthordSplitEditorLayoutState(preferredLayoutName = "INVALID_LAYOUT"))

        assertEquals(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, service.preferredLayout())
    }
}
