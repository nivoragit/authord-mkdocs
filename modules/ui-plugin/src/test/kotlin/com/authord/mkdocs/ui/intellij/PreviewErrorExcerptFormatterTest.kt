package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewErrorExcerptFormatterTest {
    @Test
    fun `extracts stderr tail when available`() {
        val message = """
            Preview server failed to start. Details: Authord process exited before readiness probe succeeded.
            stdout tail:
            INFO - booting runtime
            stderr tail:
            Traceback (most recent call last):
            ModuleNotFoundError: No module named 'material'
        """.trimIndent()

        val excerpt = extractExactErrorExcerpt(message)

        assertTrue(excerpt.contains("Traceback"))
        assertTrue(excerpt.contains("ModuleNotFoundError"))
        assertFalse(excerpt.contains("stdout tail"))
    }

    @Test
    fun `falls back to details section when tails are absent`() {
        val message = "Preview server failed to start. Details: ConfigurationError: Invalid YAML in mkdocs.yml"

        val excerpt = extractExactErrorExcerpt(message)

        assertTrue(excerpt.contains("ConfigurationError"))
        assertFalse(excerpt.contains("Preview server failed to start"))
    }

    @Test
    fun `truncates long excerpts`() {
        val message = buildString {
            append("stderr tail:\n")
            repeat(25) { index ->
                append("line-${index + 1} ")
                append("x".repeat(40))
                append('\n')
            }
        }

        val excerpt = extractExactErrorExcerpt(message, maxLines = 4, maxChars = 140)

        assertTrue(excerpt.contains("line-1"))
        assertTrue(excerpt.contains("..."))
        assertFalse(excerpt.contains("line-20"))
    }
}
