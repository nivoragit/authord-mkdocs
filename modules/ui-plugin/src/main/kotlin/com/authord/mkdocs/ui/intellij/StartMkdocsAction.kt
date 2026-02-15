package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbAware

/**
 * IntelliJ action entry point for starting MkDocs preview shell flow.
 */
class StartMkdocsAction(
    private val runtimeServiceResolver: (Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val resultPresenter: (Project, String, Boolean) -> Unit = ::presentPreviewResult,
    private val compatibilityGateServiceResolver: (Project) -> CompatibilityReleaseGateService = {
        CompatibilityReleaseGateService()
    },
    private val compatibilityRegressionsProvider: (Project) -> List<CompatibilityRegression> = { emptyList() },
) : AnAction("Start MkDocs Preview"), DumbAware {
    /**
     * Uses background update thread to respect action-system threading guidance.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    /**
     * Updates action visibility/enabled state based on project context and runtime readiness.
     */
    override fun update(event: AnActionEvent) {
        val state = resolvePresentationState(event.project)
        event.presentation.isVisible = state.visible
        event.presentation.isEnabled = state.enabled
    }

    /**
     * Delegates action invocation to project runtime integration service.
     */
    override fun actionPerformed(event: AnActionEvent) {
        invokeForProject(event.project)
    }

    internal fun resolvePresentationState(project: Project?): ActionPresentationState {
        if (project == null) {
            return ActionPresentationState(visible = false, enabled = false)
        }

        val runtimeService = runtimeServiceResolver(project)
        return ActionPresentationState(
            visible = true,
            enabled = runtimeService.canStartPreview(),
        )
    }

    internal fun invokeForProject(project: Project?): Boolean {
        if (project == null) {
            return false
        }

        val compatibilityDecision = compatibilityGateServiceResolver(project).evaluate(
            regressions = compatibilityRegressionsProvider(project),
            projectId = project.locationHash,
            instanceId = "default",
        )
        if (compatibilityDecision.blocked) {
            resultPresenter(project, compatibilityDecision.summary, false)
            return false
        }

        val runtimeService = runtimeServiceResolver(project)
        val result = runtimeService.startPreview(PreviewStartTrigger.ACTION)
        val message = formatPreviewResultMessage(result)
        resultPresenter(project, message, result.success)
        return result.success
    }
}

internal data class ActionPresentationState(
    val visible: Boolean,
    val enabled: Boolean,
)
