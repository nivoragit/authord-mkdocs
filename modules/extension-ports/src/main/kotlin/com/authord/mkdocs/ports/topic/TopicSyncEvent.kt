package com.authord.mkdocs.ports.topic

/**
 * Event categories emitted from topic-tree synchronization flows.
 */
enum class TopicSyncEventType {
    STARTUP_RECONCILIATION,
    WATCHER_TRIGGER,
    MUTATION_APPLY,
    MUTATION_ROLLBACK,
    VALIDATION_REPORT,
    COMPATIBILITY_GATE,
}

/**
 * Structured observability event for mutation and reconciliation telemetry.
 */
data class TopicSyncEvent(
    val eventType: TopicSyncEventType,
    val projectId: String,
    val instanceId: String,
    val transactionId: String,
    val severity: String,
    val message: String,
)
