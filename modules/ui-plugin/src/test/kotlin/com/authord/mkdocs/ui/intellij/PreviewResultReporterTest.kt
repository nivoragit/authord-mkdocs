package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationFailureReason
import com.authord.mkdocs.ui.ActivationResult
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewResultReporterTest {
    @Test
    fun `formats started message for successful activation`() {
        val result = ActivationResult(
            success = true,
            previewUrl = "http://127.0.0.1:8000/",
            message = "Activation completed",
        )

        val formatted = formatPreviewResultMessage(result)

        assertEquals("MkDocs preview started: http://127.0.0.1:8000/", formatted)
    }

    @Test
    fun `formats already-running message for successful reuse`() {
        val result = ActivationResult(
            success = true,
            previewUrl = "http://127.0.0.1:8000/",
            message = "Preview already running.",
        )

        val formatted = formatPreviewResultMessage(result)

        assertTrue(formatted.contains("already running", ignoreCase = true))
    }

    @Test
    fun `formats failure message from activation details`() {
        val result = ActivationResult(
            success = false,
            reason = ActivationFailureReason.START_FAILED,
            message = "Preview server failed to start.",
        )

        val formatted = formatPreviewResultMessage(result)

        assertEquals("Preview server failed to start.", formatted)
    }

    @Test
    fun `failure reporting path remains invokable for project notifications`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-reporter")

        presentPreviewResult(
            project = project,
            message = "Preview server failed to start.",
            success = false,
        )

        assertFalse(project.isDisposed)
    }
}
