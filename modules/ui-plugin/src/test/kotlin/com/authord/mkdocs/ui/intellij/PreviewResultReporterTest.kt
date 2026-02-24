package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationFailureReason
import com.authord.mkdocs.ui.ActivationDiagnostics
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

        assertEquals("Authord preview started: http://127.0.0.1:8000/", formatted)
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
            message = "ModuleNotFoundError: No module named 'material'",
        )

        val formatted = formatPreviewResultMessage(result)

        assertTrue(formatted.contains("Authord preview failed to start"))
        assertTrue(formatted.contains("mkdocs-material"))
    }

    @Test
    fun `formats wrapped bootstrap failure using suggested package diagnostics`() {
        val result = ActivationResult(
            success = false,
            reason = ActivationFailureReason.BOOTSTRAP_FAILED,
            message = "Runtime bootstrap failed. Details: MkDocs config references Material extensions, but mkdocs-material is not installed in the preview Python environment.",
            diagnostics = ActivationDiagnostics(
                pythonExecutable = "/tmp/.venv/bin/python",
                uvExecutablePath = "/tmp/tools/uv",
                suggestedPackage = "mkdocs-material",
            ),
        )

        val formatted = formatPreviewResultMessage(result)

        assertTrue(formatted.contains("mkdocs-material"))
        assertTrue(formatted.contains("requirements.txt"))
        assertTrue(formatted.contains("/tmp/tools/uv pip install --python /tmp/.venv/bin/python mkdocs-material"))
    }

    @Test
    fun `formats fallback failure message when activation message is blank`() {
        val result = ActivationResult(
            success = false,
            reason = ActivationFailureReason.START_FAILED,
            message = "   ",
        )

        val formatted = formatPreviewResultMessage(result)

        assertTrue(formatted.contains("Authord preview failed to start"))
    }

    @Test
    fun `formats success message with unknown url when preview url is blank`() {
        val result = ActivationResult(
            success = true,
            previewUrl = "  ",
            message = "Activation completed",
        )

        val formatted = formatPreviewResultMessage(result)

        assertEquals("Authord preview started: <unknown-url>", formatted)
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

    @Test
    fun `success reporting path remains invokable without notification dispatch`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-reporter-success")

        presentPreviewResult(
            project = project,
            message = "Preview server is running.",
            success = true,
        )

        assertFalse(project.isDisposed)
    }
}
