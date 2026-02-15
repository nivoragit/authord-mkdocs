package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewCompatibilityCriteriaTest {
    @Test
    fun `classifier maps high-severity rubric categories to high`() {
        val classifier = CompatibilitySeverityClassifier()
        val kinds = listOf(
            CompatibilityRegressionKind.PREVIEW_START_FAILURE,
            CompatibilityRegressionKind.RUNTIME_URL_DETECTION_FAILURE,
            CompatibilityRegressionKind.PLUGIN_CRASH_OR_FREEZE,
            CompatibilityRegressionKind.DESTRUCTIVE_DATA_LOSS,
        )

        val severities = kinds.map { kind ->
            classifier.classify(
                CompatibilityRegression(
                    id = "reg-$kind",
                    kind = kind,
                    description = "Regression for $kind",
                    resolved = false,
                ),
            )
        }

        assertTrue(severities.all { it == CompatibilitySeverity.HIGH })
    }

    @Test
    fun `release gate blocks unresolved high-severity compatibility regressions`() {
        val gate = CompatibilityReleaseGateService()
        val decision = gate.evaluate(
            regressions = listOf(
                CompatibilityRegression(
                    id = "reg-1",
                    kind = CompatibilityRegressionKind.RUNTIME_URL_DETECTION_FAILURE,
                    description = "Preview unreachable due to URL detection failure",
                    resolved = false,
                ),
            ),
            projectId = "project-compat",
            instanceId = "default",
        )

        assertTrue(decision.blocked)
        assertEquals(1, decision.unresolvedHighSeverity.size)
    }

    @Test
    fun `release gate passes when high-severity findings are resolved`() {
        val gate = CompatibilityReleaseGateService()
        val decision = gate.evaluate(
            regressions = listOf(
                CompatibilityRegression(
                    id = "reg-2",
                    kind = CompatibilityRegressionKind.PREVIEW_START_FAILURE,
                    description = "Preview entrypoint issue fixed",
                    resolved = true,
                ),
                CompatibilityRegression(
                    id = "reg-3",
                    kind = CompatibilityRegressionKind.OTHER,
                    description = "Minor UI drift",
                    resolved = false,
                ),
            ),
            projectId = "project-compat",
            instanceId = "default",
        )

        assertFalse(decision.blocked)
        assertTrue(decision.unresolvedHighSeverity.isEmpty())
    }
}
