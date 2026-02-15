package com.authord.mkdocs.ui.intellij

/**
 * Compatibility regression classes used by the release severity rubric.
 */
enum class CompatibilityRegressionKind {
    PREVIEW_START_FAILURE,
    RUNTIME_URL_DETECTION_FAILURE,
    PLUGIN_CRASH_OR_FREEZE,
    DESTRUCTIVE_DATA_LOSS,
    OTHER,
}

/**
 * Severity levels produced by compatibility classification.
 */
enum class CompatibilitySeverity {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * One compatibility regression finding from verification evidence.
 */
data class CompatibilityRegression(
    val id: String,
    val kind: CompatibilityRegressionKind,
    val description: String,
    val resolved: Boolean,
)

/**
 * Classifies compatibility findings according to the spec release rubric.
 */
class CompatibilitySeverityClassifier {
    /**
     * Resolves a single compatibility severity.
     */
    fun classify(regression: CompatibilityRegression): CompatibilitySeverity {
        if (regression.resolved) {
            return CompatibilitySeverity.LOW
        }

        return when (regression.kind) {
            CompatibilityRegressionKind.PREVIEW_START_FAILURE,
            CompatibilityRegressionKind.RUNTIME_URL_DETECTION_FAILURE,
            CompatibilityRegressionKind.PLUGIN_CRASH_OR_FREEZE,
            CompatibilityRegressionKind.DESTRUCTIVE_DATA_LOSS,
            -> CompatibilitySeverity.HIGH

            CompatibilityRegressionKind.OTHER -> CompatibilitySeverity.MEDIUM
        }
    }
}
