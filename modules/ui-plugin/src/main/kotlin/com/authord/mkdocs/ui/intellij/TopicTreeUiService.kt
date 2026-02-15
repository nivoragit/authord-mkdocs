package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicTreeCommand

/**
 * UI-facing service boundary for topic-tree actions and instance selection.
 *
 * Usage contract:
 * - Inputs: typed [TopicTreeCommand] instances and instance IDs.
 * - Outputs: sync outcomes and active-instance selection results.
 * - Errors: typed failures from application-service and registry ports.
 *
 * Usage example:
 * ```kotlin
 * uiService.dispatch(command)
 * uiService.selectInstance("default")
 * uiService.refreshActiveTree()
 * ```
 */
interface TopicTreeUiService {
    /**
     * Dispatches one topic-tree command from UI actions/drag-drop flows.
     *
     * Input: one [command] produced by UI actions or drag/drop.
     * Output: [TopicGatewayResult.Success] with synchronized mutation outcome.
     * Errors/failure modes: typed validation/orchestration/IO failures from downstream services.
     */
    fun dispatch(command: TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Forces refresh of active tree model in UI state.
     *
     * Input: none.
     * Output: [TopicGatewayResult.Success] with refresh reconciliation outcome.
     * Errors/failure modes: typed reconciliation/config failures from application services.
     */
    fun refreshActiveTree(): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Selects an instance for subsequent UI-scoped operations.
     *
     * Input: target [instanceId] chosen by the user.
     * Output: [TopicGatewayResult.Success] with the active instance context.
     * Errors/failure modes: INSTANCE_SCOPE errors for unknown/unavailable instances.
     */
    fun selectInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef>
}
