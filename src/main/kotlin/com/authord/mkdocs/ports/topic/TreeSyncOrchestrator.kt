package com.authord.mkdocs.ports.topic

/**
 * Port for atomic application of tree/config/file mutations with rollback/compensation.
 *
 * Usage contract:
 * - Inputs: [TopicSyncTransaction] descriptors.
 * - Outputs: [TopicSyncOutcome] states for apply/rollback/compensation.
 * - Errors: [TopicSyncErrorCode.ORCHESTRATION], [TopicSyncErrorCode.CONFIG_WRITE], and
 *   [TopicSyncErrorCode.FILE_IO].
 *
 * Usage example:
 * ```kotlin
 * val apply = orchestrator.apply(transaction)
 * if (apply is TopicGatewayResult.Failure) {
 *     orchestrator.rollback(transaction.transactionId, "apply failed")
 * }
 * ```
 */
interface TreeSyncOrchestrator {
    /**
     * Applies the transaction and ensures atomic completion.
     *
     * Input: [transaction] including command + file operations.
     * Output: [TopicGatewayResult.Success] with apply state and compensation metadata.
     * Errors/failure modes: ORCHESTRATION/CONFIG_WRITE/FILE_IO on partial or complete apply failure.
     */
    fun apply(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Rolls back an already-started transaction.
     *
     * Inputs: prior [transactionId] and human-readable [reason].
     * Output: rollback [TopicSyncOutcome] status.
     * Errors/failure modes: ORCHESTRATION when rollback could not be executed.
     */
    fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome>

    /**
     * Executes compensating actions when full rollback is not feasible.
     *
     * Inputs: prior [transactionId] and [reason].
     * Output: compensated [TopicSyncOutcome] status.
     * Errors/failure modes: ORCHESTRATION when compensation cannot be completed.
     */
    fun compensate(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome>
}
