package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationResult
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val NOTIFICATION_GROUP_ID = "Authord MkDocs Notifications"
private const val NOTIFICATION_TITLE = "Authord MkDocs"

/**
 * Emits preview start results in a way that is visible during runIde sessions.
 */
internal fun presentPreviewResult(project: Project, message: String, success: Boolean) {
    val level = if (success) "INFO" else "ERROR"
    println("[Authord MkDocs][$level][${project.name}] $message")

    if (!success) {
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP_ID)
                .createNotification(NOTIFICATION_TITLE, message, NotificationType.ERROR)
                .notify(project)
        }
    }
}

/**
 * Formats activation outcomes for action/tool-window reporting.
 */
internal fun formatPreviewResultMessage(result: ActivationResult): String {
    if (!result.success) {
        return result.message.ifBlank { "Preview start failed." }
    }

    val resolvedUrl = result.previewUrl.ifBlank { "<unknown-url>" }
    return if (result.message.equals("Preview already running.", ignoreCase = true)) {
        "MkDocs preview already running: $resolvedUrl"
    } else {
        "MkDocs preview started: $resolvedUrl"
    }
}
