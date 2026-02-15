package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaseUrlDetectorStdoutTest {
    private val detector = BaseUrlDetector()

    @Test
    fun `detects runtime base url from multiline stdout`() {
        val stdout = """
            INFO    - Building documentation...
            INFO    - Serving on http://127.0.0.1:8123/
            INFO    - Press CTRL+C to quit
        """.trimIndent()

        assertEquals("http://127.0.0.1:8123/", detector.detectBaseUrl(stdout))
    }

    @Test
    fun `trims trailing punctuation from detected url`() {
        val stdout = "Started successfully at https://preview.example/docs/."
        assertEquals("https://preview.example/docs/", detector.detectBaseUrl(stdout))
    }

    @Test
    fun `returns null when stdout has no http or https url`() {
        assertNull(detector.detectBaseUrl("startup finished without exposed url"))
    }
}
