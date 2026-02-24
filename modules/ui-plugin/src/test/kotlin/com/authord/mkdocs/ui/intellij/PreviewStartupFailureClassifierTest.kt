package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewStartupFailureClassifierTest {
    @Test
    fun `classifies missing material dependency with actionable guidance`() {
        val failure = PreviewStartupFailureClassifier.classify(
            rawMessage = "ModuleNotFoundError: No module named 'material'",
            context = PreviewStartupFailureContext(pythonExecutable = "/tmp/.venv/bin/python"),
        )

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("mkdocs-material", failure.installPackage)
        assertEquals(PreviewFailureConfidence.HIGH, failure.confidence)
        assertTrue(failure.nextStep.contains("/tmp/.venv/bin/python -m pip install mkdocs-material"))
        assertTrue(failure.persistenceGuidance.contains("requirements.txt"))
    }

    @Test
    fun `classifies missing pymdown dependency`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "ModuleNotFoundError: No module named 'pymdownx'",
        )

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("pymdown-extensions", failure.installPackage)
        assertEquals(PreviewFailureConfidence.HIGH, failure.confidence)
    }

    @Test
    fun `classifies generic missing module with low confidence`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "ModuleNotFoundError: No module named 'foo_bar'",
        )

        assertEquals(PreviewStartupFailureCategory.MISSING_DEPENDENCY, failure.category)
        assertEquals("foo-bar", failure.installPackage)
        assertEquals("foo_bar", failure.missingModule)
        assertEquals(PreviewFailureConfidence.LOW, failure.confidence)
    }

    @Test
    fun `classifies mkdocs config parse errors with line column context`() {
        val failure = PreviewStartupFailureClassifier.classify(
            "MkDocs encountered an error parsing the configuration file at line 12, column 4",
        )

        assertEquals(PreviewStartupFailureCategory.CONFIG_PARSE_ERROR, failure.category)
        assertTrue(failure.reason.contains("line 12, column 4"))
        assertEquals("line 12, column 4", failure.configContext)
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
