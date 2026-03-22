package com.authord.mkdocs.ui.intellij

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewFailureNotifierTest {
    @BeforeTest
    fun clearDedupeState() {
        PreviewFailureNotifier.clearDedupeStateForTests()
    }

    @Test
    fun `notification dedupe suppresses repeated identical failures`() {
        var now = 1_000L
        var emitted = 0
        val notifier = PreviewFailureNotifier(
            nowMillisProvider = { now },
            dedupeWindowMillis = 60_000L,
            emit = { _, _ -> emitted += 1 },
        )
        val project = IntellijTestFixtures.project(locationHash = "failure-notifier-dedupe")
        val failure = PreviewStartupFailure(
            category = PreviewStartupFailureCategory.MISSING_DEPENDENCY,
            reason = "Missing dependency",
            nextStep = "Install and retry",
            installPackage = "mkdocs-material",
            primaryLine = "No module named 'material'",
        )

        notifier.notifyStartupFailure(project, failure, "/tmp/mkdocs.yml")
        notifier.notifyStartupFailure(project, failure, "/tmp/mkdocs.yml")

        assertEquals(1, emitted)

        now += 61_000L
        notifier.notifyStartupFailure(project, failure, "/tmp/mkdocs.yml")
        assertEquals(2, emitted)
    }
}

