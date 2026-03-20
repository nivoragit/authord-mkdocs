package com.authord.mkdocs.ui.intellij

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class PreviewFailureNotifier(
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
    private val dedupeWindowMillis: Long = 30_000L,
    private val emit: (Project, PreviewFailureNotificationPayload) -> Unit = ::emitFailureNotification,
) {
    fun notifyStartupFailure(
        project: Project,
        failure: PreviewStartupFailure,
        configPath: String?,
        exactErrorExcerpt: String? = null,
        onRetry: (() -> Unit)? = null,
    ) {
        val fingerprint = fingerprint(failure, configPath)
        if (!shouldNotify(project.locationHash, fingerprint)) {
            return
        }

        emit(
            project,
            PreviewFailureNotificationPayload(
                failure = failure,
                configPath = configPath,
                exactErrorExcerpt = exactErrorExcerpt?.trim().orEmpty().ifBlank { null },
                onRetry = onRetry,
            ),
        )
    }

    private fun shouldNotify(projectId: String, fingerprint: String): Boolean {
        val now = nowMillisProvider()
        val projectCache = dedupeByProject.computeIfAbsent(projectId) { ConcurrentHashMap() }
        val last = projectCache[fingerprint]
        if (last != null && now - last < dedupeWindowMillis) {
            return false
        }
        projectCache[fingerprint] = now
        return true
    }

    private fun fingerprint(failure: PreviewStartupFailure, configPath: String?): String {
        val normalizedLine = failure.primaryLine.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val normalizedConfig = configPath.orEmpty().replace('\\', '/').lowercase(Locale.ROOT)
        return "${failure.category}|$normalizedLine|$normalizedConfig"
    }

    companion object {
        private val dedupeByProject = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

        fun clearDedupeStateForTests() {
            dedupeByProject.clear()
        }
    }
}

internal data class PreviewFailureNotificationPayload(
    val failure: PreviewStartupFailure,
    val configPath: String?,
    val exactErrorExcerpt: String?,
    val onRetry: (() -> Unit)?,
)

private fun emitFailureNotification(project: Project, payload: PreviewFailureNotificationPayload) {
    val failure = payload.failure
    val message = buildString {
        append("Authord preview failed to start.\n")
        append("Reason: ")
        append(failure.reason)
        append("\n")
        append("Next step: ")
        append(failure.nextStep)
        payload.exactErrorExcerpt?.let { excerpt ->
            append("\n")
            append("Exact error (truncated):\n")
            append(excerpt)
        }
        append("\n")
        append("For full details, open runtime logs (idea.log).")
    }

    println("[Authord][ERROR][${project.name}] $message")
    val notification = runCatching {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Authord Notifications")
            .createNotification("Authord", message, NotificationType.ERROR)
    }.getOrNull() ?: return

    payload.onRetry?.let { retry ->
        notification.addAction(
            NotificationAction.createSimple("Retry Authord Preview") {
                retry()
            },
        )
    }

    payload.configPath?.let { configPath ->
        notification.addAction(
            NotificationAction.createSimple("Open mkdocs.yml") {
                openPathInEditor(project, configPath)
            },
        )
    }

    if (failure.installPackage != null) {
        notification.addAction(
            NotificationAction.createSimple("Install Missing Dependency…") {
                val command = "uv pip install ${failure.installPackage}"
                presentAuthordNotification(
                    project,
                    "Install suggestion: $command",
                    NotificationType.INFORMATION,
                )
            },
        )
    }

    notification.addAction(
        NotificationAction.createSimple("Open Runtime Logs") {
            openRuntimeLog(project)
        },
    )
    notification.notify(project)
}

private fun openPathInEditor(project: Project, path: String) {
    val nio = runCatching { Path.of(path) }.getOrNull() ?: return
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nio) ?: return
    runCatching {
        FileEditorManager.getInstance(project).openFile(file, true)
    }
}

private fun openRuntimeLog(project: Project) {
    val logPath = Path.of(PathManager.getLogPath()).resolve("idea.log")
    openPathInEditor(project, logPath.toString())
}
