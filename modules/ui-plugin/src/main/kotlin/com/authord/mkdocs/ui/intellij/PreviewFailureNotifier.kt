package com.authord.mkdocs.ui.intellij

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.datatransfer.StringSelection
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
        onRetry: (() -> Unit)? = null,
        onRetryDependencySetup: (() -> Unit)? = null,
        onStartAnyway: (() -> Unit)? = null,
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
                onRetry = onRetry,
                onRetryDependencySetup = onRetryDependencySetup,
                onStartAnyway = onStartAnyway,
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
    val onRetry: (() -> Unit)?,
    val onRetryDependencySetup: (() -> Unit)?,
    val onStartAnyway: (() -> Unit)?,
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
        if (failure.persistenceGuidance.isNotBlank()) {
            append("\n")
            append("Permanent fix: ")
            append(failure.persistenceGuidance)
        }
        if (!failure.dependencyDeclarationHint.isNullOrBlank()) {
            append("\n")
            append("Dependency hint: ")
            append(failure.dependencyDeclarationHint)
        }
    }

    println("[Authord][ERROR][${project.name}] $message")
    val notification = runCatching {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Authord Notifications")
            .createNotification("Authord", message, NotificationType.ERROR)
    }.getOrNull() ?: return

    payload.onRetryDependencySetup?.let { retryDeps ->
        notification.addAction(
            NotificationAction.createSimple("Retry dependency setup") {
                retryDeps()
            },
        )
    }

    payload.onStartAnyway?.let { startAnyway ->
        notification.addAction(
            NotificationAction.createSimple("Start Anyway") {
                startAnyway()
            },
        )
    }

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

    notification.addAction(
        NotificationAction.createSimple("Open Terminal") {
            openTerminalGuidance(project, failure.installCommand)
        },
    )

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

private fun openTerminalGuidance(project: Project, installCommand: String?) {
    if (!installCommand.isNullOrBlank()) {
        CopyPasteManager.getInstance().setContents(StringSelection(installCommand))
    }
    val projectPath = project.basePath ?: "<project-root>"
    val guidance = if (installCommand.isNullOrBlank()) {
        "Open a terminal in $projectPath and rerun dependency setup."
    } else {
        "Open a terminal in $projectPath. Install command copied to clipboard:\n$installCommand"
    }
    presentAuthordNotification(project, guidance, NotificationType.INFORMATION)
}
