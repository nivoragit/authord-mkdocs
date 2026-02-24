package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val NOTIFICATION_GROUP_ID = "Authord Notifications"
private const val NOTIFICATION_TITLE = "Authord"

/**
 * Emits preview start results in a way that is visible during runIde sessions.
 */
internal fun presentPreviewResult(project: Project, message: String, success: Boolean) {
    if (success) {
        val normalized = message.trim()
        if (normalized.isNotEmpty()) {
            println("[Authord][INFO][${project.name}] $normalized")
        }
        return
    }
    presentAuthordNotification(project, message, NotificationType.ERROR)
}

/**
 * Emits a generic Authord notification through IntelliJ notification group.
 */
internal fun presentAuthordNotification(
    project: Project?,
    message: String,
    type: NotificationType,
) {
    val normalized = message.trim()
    if (normalized.isEmpty()) {
        return
    }
    val level = when (type) {
        NotificationType.INFORMATION -> "INFO"
        NotificationType.WARNING -> "WARN"
        NotificationType.ERROR -> "ERROR"
        else -> type.name
    }
    val projectName = project?.name ?: "<no-project>"
    println("[Authord][$level][$projectName] $normalized")

    val activeProject = project ?: return
    runCatching {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(NOTIFICATION_TITLE, normalized, type)
            .notify(activeProject)
    }
}

/**
 * Formats activation outcomes for action/tool-window reporting.
 */
internal fun formatPreviewResultMessage(result: ActivationResult): String {
    if (!result.success) {
        val failure = PreviewStartupFailureClassifier.classify(
            result.message.ifBlank { "Preview start failed." },
        )
        return buildString {
            append("Authord preview failed to start. ")
            append("Reason: ")
            append(failure.reason)
            append(" ")
            append("Next step: ")
            append(failure.nextStep)
        }
    }

    val resolvedUrl = result.previewUrl.ifBlank { "<unknown-url>" }
    return if (result.message.equals("Preview already running.", ignoreCase = true)) {
        "Authord preview already running: $resolvedUrl"
    } else {
        "Authord preview started: $resolvedUrl"
    }
}
