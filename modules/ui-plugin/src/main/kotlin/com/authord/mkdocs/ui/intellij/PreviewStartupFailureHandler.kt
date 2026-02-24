package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

internal class PreviewStartupFailureHandler(
    private val routerResolver: () -> PreviewRouterService = { PreviewRouterService() },
    private val bypassStoreResolver: (Project) -> FailureBypassStore? = { project ->
        runCatching { project.getService(FailureBypassStore::class.java) }.getOrNull()
    },
    private val notifier: PreviewFailureNotifier = PreviewFailureNotifier(),
    private val fallbackInvoker: (Project, String) -> Unit = ::reopenFileForRoutingRefresh,
) {
    fun handleFailure(
        project: Project,
        result: ActivationResult,
        selectedPath: String?,
        onRetry: (() -> Unit)? = null,
        onRetryDependencySetup: (() -> Unit)? = null,
        onStartAnyway: (() -> Unit)? = null,
    ) {
        val failure = PreviewStartupFailureClassifier.classify(
            result.message.ifBlank { "Preview start failed." },
            context = PreviewStartupFailureContext(
                pythonExecutable = result.diagnostics.pythonExecutable.ifBlank { null },
                dependencyDeclarationHint = result.diagnostics.dependencyDeclarationHint.ifBlank { null },
            ),
        )

        var configPath: String? = null
        val bypassStore = bypassStoreResolver(project)
        if (!selectedPath.isNullOrBlank()) {
            val decision = routerResolver().decide(project, selectedPath)
            configPath = decision.configPath
            if (decision.scopeStatus == DocsScopeStatus.DOCS_SCOPED) {
                bypassStore?.markBypass(selectedPath, configPath)
                fallbackInvoker(project, selectedPath)
            }
        }

        fun wrapAction(action: (() -> Unit)?): (() -> Unit)? {
            if (action == null) {
                return null
            }
            return {
                if (!selectedPath.isNullOrBlank()) {
                    bypassStore?.clearBypass(selectedPath, configPath)
                }
                action()
            }
        }

        val wrappedRetry = wrapAction(onRetry)
        val wrappedRetryDependencySetup = wrapAction(onRetryDependencySetup ?: onRetry)
        val wrappedStartAnyway = wrapAction(onStartAnyway)

        notifier.notifyStartupFailure(
            project = project,
            failure = failure,
            configPath = configPath,
            onRetry = wrappedRetry,
            onRetryDependencySetup = wrappedRetryDependencySetup,
            onStartAnyway = wrappedStartAnyway,
        )
    }
}

internal fun reopenFileForRoutingRefresh(project: Project, selectedPath: String) {
    if (project.isDisposed) {
        return
    }
    val file = runCatching {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(selectedPath)
    }.getOrNull() ?: return
    val task: () -> Unit = task@{
        if (project.isDisposed) {
            return@task
        }
        runCatching {
            val manager = FileEditorManager.getInstance(project)
            manager.closeFile(file)
            manager.openFile(file, true)
        }
    }

    val app = ApplicationManager.getApplication()
    if (app == null) {
        task()
        return
    }
    app.invokeLater(task, ModalityState.any())
}
