package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicSyncError
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome

/**
 * User-facing recovery guidance for a failed mutation or drag/drop command.
 */
data class TopicTreeRecoveryMessage(
    val summary: String,
    val guidance: String,
    val nonDestructive: Boolean = true,
)

/**
 * Standard dispatch result envelope for topic-tree UI controllers.
 */
data class TopicMutationDispatchResult(
    val result: TopicGatewayResult<TopicSyncOutcome>,
    val recovery: TopicTreeRecoveryMessage? = null,
)

/**
 * Maps typed sync errors into non-destructive user guidance.
 */
class TopicTreeFailureRecoveryPresenter {
    /**
     * Builds recovery guidance for one failed operation.
     */
    fun present(operationLabel: String, error: TopicSyncError): TopicTreeRecoveryMessage {
        val operation = operationLabel.trim().ifBlank { "Requested operation" }
        val detail = error.detail.trim()
        return when (error.code) {
            TopicSyncErrorCode.VALIDATION -> TopicTreeRecoveryMessage(
                summary = "$operation was rejected",
                guidance = "No changes were applied. Update the request and retry. Detail: $detail",
            )

            TopicSyncErrorCode.CONFIG_WRITE,
            TopicSyncErrorCode.FILE_IO,
            TopicSyncErrorCode.ORCHESTRATION,
            -> TopicTreeRecoveryMessage(
                summary = "$operation failed safely",
                guidance = "Rollback/compensation preserved the existing tree state. Detail: $detail",
            )

            TopicSyncErrorCode.INSTANCE_SCOPE -> TopicTreeRecoveryMessage(
                summary = "$operation is out of active instance scope",
                guidance = "Select the intended Authord instance and retry. Detail: $detail",
            )

            TopicSyncErrorCode.RECONCILIATION -> TopicTreeRecoveryMessage(
                summary = "$operation requires reconciliation follow-up",
                guidance = "Review validation and unlinked-file reports before retrying. Detail: $detail",
            )

            TopicSyncErrorCode.CONFIG_PARSE -> TopicTreeRecoveryMessage(
                summary = "$operation could not read project config",
                guidance = "Correct config syntax/path and retry the operation. Detail: $detail",
            )

            TopicSyncErrorCode.UNSUPPORTED -> TopicTreeRecoveryMessage(
                summary = "$operation is unavailable in this environment",
                guidance = "Use the supported runtime path or enable the required integration. Detail: $detail",
            )
        }
    }
}
