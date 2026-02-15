package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicSyncEvent
import com.authord.mkdocs.ports.topic.TopicSyncEventType

/**
 * Emits structured topic-tree observability events.
 */
class TopicTreeObservability(
    private val sink: (TopicSyncEvent) -> Unit = {},
) {
    /**
     * Emits startup reconciliation lifecycle event.
     */
    fun emitStartupReconciliation(
        projectId: String,
        instanceId: String,
        message: String,
        severity: String,
    ) {
        sink(
            TopicSyncEvent(
                eventType = TopicSyncEventType.STARTUP_RECONCILIATION,
                projectId = projectId,
                instanceId = instanceId,
                transactionId = "startup:$instanceId",
                severity = severity,
                message = message,
            ),
        )
    }

    /**
     * Emits watcher-trigger event with non-destructive follow-up detail.
     */
    fun emitWatcherTrigger(
        projectId: String,
        instanceId: String,
        reason: String,
        followUp: WatcherFollowUp,
    ) {
        sink(
            TopicSyncEvent(
                eventType = TopicSyncEventType.WATCHER_TRIGGER,
                projectId = projectId,
                instanceId = instanceId,
                transactionId = "watcher:$instanceId",
                severity = "info",
                message = "$reason (follow-up=$followUp)",
            ),
        )
    }

    /**
     * Emits validation summary event.
     */
    fun emitValidationReport(
        projectId: String,
        instanceId: String,
        issueCount: Int,
    ) {
        sink(
            TopicSyncEvent(
                eventType = TopicSyncEventType.VALIDATION_REPORT,
                projectId = projectId,
                instanceId = instanceId,
                transactionId = "validation:$instanceId",
                severity = if (issueCount == 0) "info" else "warn",
                message = "Validation issues: $issueCount",
            ),
        )
    }

    /**
     * Emits compatibility gate decision event.
     */
    fun emitCompatibilityGate(
        projectId: String,
        instanceId: String,
        blocked: Boolean,
        unresolvedHighCount: Int,
    ) {
        sink(
            TopicSyncEvent(
                eventType = TopicSyncEventType.COMPATIBILITY_GATE,
                projectId = projectId,
                instanceId = instanceId,
                transactionId = "compatibility:$instanceId",
                severity = if (blocked) "error" else "info",
                message = "Compatibility gate blocked=$blocked unresolvedHigh=$unresolvedHighCount",
            ),
        )
    }
}
