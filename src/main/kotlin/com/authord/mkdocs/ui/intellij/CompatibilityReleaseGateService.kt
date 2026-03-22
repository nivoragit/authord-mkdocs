package com.authord.mkdocs.ui.intellij

/**
 * Decision envelope for compatibility release gating.
 */
data class CompatibilityReleaseGateDecision(
    val blocked: Boolean,
    val unresolvedHighSeverity: List<CompatibilityRegression>,
    val summary: String,
)

/**
 * Evaluates compatibility findings and applies release-blocking policy.
 */
class CompatibilityReleaseGateService(
    private val severityClassifier: CompatibilitySeverityClassifier = CompatibilitySeverityClassifier(),
    private val observability: TopicTreeObservability = TopicTreeObservability(),
) {
    /**
     * Evaluates [regressions] and returns a gate decision.
     */
    fun evaluate(
        regressions: List<CompatibilityRegression>,
        projectId: String,
        instanceId: String,
    ): CompatibilityReleaseGateDecision {
        val unresolvedHigh = regressions.filter { regression ->
            !regression.resolved && severityClassifier.classify(regression) == CompatibilitySeverity.HIGH
        }
        val blocked = unresolvedHigh.isNotEmpty()
        val summary = if (blocked) {
            "Release blocked: ${unresolvedHigh.size} unresolved high-severity compatibility regression(s)"
        } else {
            "Compatibility gate passed"
        }
        observability.emitCompatibilityGate(
            projectId = projectId,
            instanceId = instanceId,
            blocked = blocked,
            unresolvedHighCount = unresolvedHigh.size,
        )

        return CompatibilityReleaseGateDecision(
            blocked = blocked,
            unresolvedHighSeverity = unresolvedHigh,
            summary = summary,
        )
    }
}
