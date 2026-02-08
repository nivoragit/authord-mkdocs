package com.authord.mkdocs.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaseUrlDetectorTest {
    private val detector = BaseUrlDetector()

    @Test
    fun `detects base url from startup output`() {
        val output = "INFO - Serving on http://127.0.0.1:8000/"

        assertEquals("http://127.0.0.1:8000/", detector.detectBaseUrl(output))
    }

    @Test
    fun `returns null when url is absent`() {
        assertNull(detector.detectBaseUrl("no usable url in this output"))
    }
}
