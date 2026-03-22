package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicInstanceRef

/**
 * Reconciliation input bundle for a single instance scope.
 */
data class InstanceReconciliationInput(
    val instance: TopicInstanceRef,
    val config: MkDocsConfigDocument,
    val docsMarkdownPaths: List<String>,
)

/**
 * Multi-instance reconciliation result keyed by instance ID.
 */
data class MultiInstanceReconciliationResult(
    val statesByInstanceId: Map<String, StartupTreeState>,
)

/**
 * Runs startup/file-change reconciliation independently across registered instances.
 */
class MultiInstanceReconciliationCoordinator(
    private val startupReconciliationCoordinator: StartupReconciliationCoordinator = StartupReconciliationCoordinator(),
) {
    /**
     * Reconciles all [inputs] with nav-first, non-destructive policies.
     */
    fun reconcile(
        projectId: String,
        inputs: List<InstanceReconciliationInput>,
    ): MultiInstanceReconciliationResult {
        val statesByInstanceId = linkedMapOf<String, StartupTreeState>()
        inputs.sortedBy { it.instance.instanceId }.forEach { input ->
            val state = startupReconciliationCoordinator.reconcile(
                config = input.config,
                docsMarkdownPaths = input.docsMarkdownPaths,
                projectId = projectId,
                instanceId = input.instance.instanceId,
            )
            statesByInstanceId[input.instance.instanceId] = state
        }
        return MultiInstanceReconciliationResult(statesByInstanceId)
    }
}
