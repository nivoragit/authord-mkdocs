package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path

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
                uvExecutablePath = result.diagnostics.uvExecutablePath.ifBlank { null },
                dependencyDeclarationHint = result.diagnostics.dependencyDeclarationHint.ifBlank { null },
                suggestedPackage = result.diagnostics.suggestedPackage.ifBlank { null },
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
        if (configPath.isNullOrBlank()) {
            configPath = resolveMkdocsConfigPath(project)
        }

        val notificationFailure = scaffoldRequirementsFileForDependencyFailure(
            project = project,
            configPath = configPath,
            failure = failure,
        )

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
            failure = notificationFailure,
            configPath = configPath,
            onRetry = wrappedRetry,
            onRetryDependencySetup = wrappedRetryDependencySetup,
            onStartAnyway = wrappedStartAnyway,
        )
    }
}

private fun resolveMkdocsConfigPath(project: Project): String? {
    val basePath = project.basePath ?: return null
    val projectRoot = runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() ?: return null
    val configPath = findMkdocsConfig(projectRoot) ?: return null
    return configPath.toAbsolutePath().normalize().toString()
}

private fun scaffoldRequirementsFileForDependencyFailure(
    project: Project,
    configPath: String?,
    failure: PreviewStartupFailure,
): PreviewStartupFailure {
    if (failure.category != PreviewStartupFailureCategory.MISSING_DEPENDENCY) {
        return failure
    }

    val requirementsPath = ensureRequirementsFile(project, configPath) ?: return failure
    val normalizedRequirementsPath = requirementsPath.toAbsolutePath().normalize().toString().replace('\\', '/')
    val scaffoldHint = "Created empty requirements.txt at `$normalizedRequirementsPath`. Add required extensions and restart preview, then retry dependency setup."
    val mergedHint = listOfNotNull(
        failure.dependencyDeclarationHint?.takeIf { it.isNotBlank() },
        scaffoldHint,
    ).joinToString(" ")
    return failure.copy(dependencyDeclarationHint = mergedHint)
}

private fun ensureRequirementsFile(project: Project, configPath: String?): Path? {
    val requirementsPath = resolveRequirementsPath(project, configPath) ?: return null
    if (Files.exists(requirementsPath)) {
        return requirementsPath.takeIf { Files.isRegularFile(it) }
    }
    return runCatching {
        Files.createDirectories(requirementsPath.parent)
        Files.createFile(requirementsPath)
        requirementsPath
    }.getOrNull()
}

private fun resolveRequirementsPath(project: Project, configPath: String?): Path? {
    val configDirectory = resolveMkdocsConfigDirectory(project, configPath) ?: return null
    return configDirectory.resolve("requirements.txt")
}

private fun resolveMkdocsConfigDirectory(project: Project, configPath: String?): Path? {
    val candidateConfig = configPath
        ?.takeIf { it.isNotBlank() }
        ?.let { rawPath -> runCatching { Path.of(rawPath).toAbsolutePath().normalize() }.getOrNull() }
    val explicitParent = candidateConfig?.parent
    if (explicitParent != null) {
        return explicitParent
    }

    val projectRoot = project.basePath
        ?.let { basePath -> runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() }
        ?: return null
    val discoveredConfig = findMkdocsConfig(projectRoot)
    return discoveredConfig?.parent ?: projectRoot
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
