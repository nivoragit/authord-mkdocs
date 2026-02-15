package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument

/**
 * Coordinates startup reconciliation with explicit nav-first, non-destructive policy.
 */
class StartupReconciliationCoordinator(
    private val startupLoader: TopicTreeStartupLoader = TopicTreeStartupLoader(),
    private val observability: TopicTreeObservability = TopicTreeObservability(),
) {
    /**
     * Reconciles nav/docs state at startup or trigger-time.
     */
    fun reconcile(
        config: MkDocsConfigDocument,
        docsMarkdownPaths: List<String>,
        projectId: String = "unknown-project",
        instanceId: String = "default",
    ): StartupTreeState {
        observability.emitStartupReconciliation(
            projectId = projectId,
            instanceId = instanceId,
            message = "Startup reconciliation started",
            severity = "info",
        )

        val state = startupLoader.load(
            config = config,
            docsMarkdownPaths = docsMarkdownPaths,
        )

        if (state.validationIssues.isNotEmpty()) {
            observability.emitValidationReport(
                projectId = projectId,
                instanceId = instanceId,
                issueCount = state.validationIssues.size,
            )
        }

        return state.copy(
            destructiveChangesApplied = false,
            instanceId = instanceId,
        )
    }
}
