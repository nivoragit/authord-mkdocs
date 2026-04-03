package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewStartupFailureClassifierTest {
    @Test
    fun `classifies missing material dependency`() {
        val failure = PreviewStartupFailureClassifier.classify("ModuleNotFoundError: No module named 'material'")

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("mkdocs-material", failure.installPackage)
    }

    @Test
    fun `classifies missing plugin dependency from config error line`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "Config value 'plugins': The \"glightbox\" plugin is not installed",
        )

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("mkdocs-glightbox", failure.installPackage)
        assertEquals(
            "MkDocs plugin `glightbox` is declared in `mkdocs.yml` but is not installed in the preview runtime.",
            failure.reason,
        )
    }

    @Test
    fun `classifies missing plugin dependency from generic plugin error line`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "The 'awesome-pages' plugin is not installed",
        )

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("mkdocs-awesome-pages", failure.installPackage)
    }

    @Test
    fun `classifies mkdocs config parse errors`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "MkDocs encountered an error parsing the configuration file",
        )

        assertEquals(PreviewStartupFailureCategory.CONFIG_PARSE_ERROR, failure.category)
    }

    @Test
    fun `classifies early process exit`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "Authord process exited before readiness probe succeeded.",
        )

        assertEquals(PreviewStartupFailureCategory.PROCESS_EXITED_EARLY, failure.category)
    }

    @Test
    fun `classifies readiness timeout`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "Authord readiness probe timed out.",
        )

        assertEquals(PreviewStartupFailureCategory.READINESS_TIMEOUT, failure.category)
    }

    @Test
    fun `classifies port bind failures`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "OSError: [Errno 98] Address already in use",
        )

        assertEquals(PreviewStartupFailureCategory.PORT_BIND_ERROR, failure.category)
    }

    @Test
    fun `classifies unknown startup failure as unknown`() {
        val failure = PreviewStartupFailureClassifier.classify("unexpected preview failure")

        assertEquals(PreviewStartupFailureCategory.UNKNOWN, failure.category)
    }
}
