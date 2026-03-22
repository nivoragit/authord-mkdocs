package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction

/**
 * Public application-service boundary for orchestration-level topic-tree operations.
 *
 * Usage contract:
 * - Inputs: transaction payloads and rollback requests.
 * - Outputs: transaction outcome states and active-instance visibility.
 * - Errors: typed failures from gateway/orchestration ports.
 *
 * Usage example:
 * ```kotlin
 * val result = appService.execute(transaction)
 * if (result is TopicGatewayResult.Failure) {
 *     appService.rollback(transaction.transactionId, "apply failed")
 * }
 * ```
 */
interface TopicTreeApplicationService {
    /**
     * Executes one atomic topic-tree synchronization transaction.
     *
     * Input: one [transaction] with command and file operations.
     * Output: [TopicGatewayResult.Success] with apply/rollback/compensation state metadata.
     * Errors/failure modes: orchestration, config, or file failures returned as typed gateway errors.
     */
    fun execute(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Requests rollback/compensation path for an in-flight transaction.
     *
     * Inputs: [transactionId] and rollback [reason].
     * Output: [TopicGatewayResult.Success] with rollback outcome metadata.
     * Errors/failure modes: typed orchestration failures when rollback cannot be applied.
     */
    fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Returns current active instance context.
     *
     * Input: none.
     * Output: [TopicGatewayResult.Success] with active instance or null.
     * Errors/failure modes: instance-scope resolution failures from registry ports.
     */
    fun activeInstance(): TopicGatewayResult<TopicInstanceRef?>

    /**
     * Hydrates in-memory aggregate state from persisted config when startup reconciliation runs.
     */
    fun hydrateTreeFromConfig(
        treeId: String,
        instanceId: String,
        config: MkDocsConfigDocument,
    ): TopicGatewayResult<Unit> {
        return TopicGatewayResult.Success(Unit)
    }
}
