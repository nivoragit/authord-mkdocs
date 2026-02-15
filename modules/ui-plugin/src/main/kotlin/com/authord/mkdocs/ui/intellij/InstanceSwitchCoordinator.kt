package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome

/**
 * Outcome emitted for instance-switch operations.
 */
data class InstanceSwitchOutcome(
    val selectedInstance: TopicInstanceRef,
    val refreshOutcome: TopicSyncOutcome?,
)

/**
 * Coordinates instance switching and optional tree refresh.
 */
class InstanceSwitchCoordinator(
    private val uiService: TopicTreeUiService,
) {
    /**
     * Selects [instanceId] and refreshes active tree state.
     */
    fun switchActiveInstance(instanceId: String): TopicGatewayResult<InstanceSwitchOutcome> {
        val selected = when (val selectionResult = uiService.selectInstance(instanceId)) {
            is TopicGatewayResult.Success -> selectionResult.value
            is TopicGatewayResult.Failure -> return selectionResult
        }
        val refresh = when (val refreshResult = uiService.refreshActiveTree()) {
            is TopicGatewayResult.Success -> refreshResult.value
            is TopicGatewayResult.Failure -> {
                return TopicGatewayResult.Failure(
                    DefaultTopicSyncError(
                        TopicSyncErrorCode.RECONCILIATION,
                        "Instance selected but tree refresh failed: ${refreshResult.error.detail}",
                    ),
                )
            }
        }
        return TopicGatewayResult.Success(
            InstanceSwitchOutcome(
                selectedInstance = selected,
                refreshOutcome = refresh,
            ),
        )
    }
}
