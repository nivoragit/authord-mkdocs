package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.RuntimeServerConfig
import com.authord.mkdocs.runtime.UvBootstrapService

/**
 * Result returned by plugin activation flow.
 */
data class ActivationResult(
    val success: Boolean,
    val reason: ActivationFailureReason? = null,
    val message: String = "",
    val previewUrl: String = "",
)

/**
 * Orchestrates first activation bootstrap, runtime startup, and preview opening.
 */
class PluginActivationService(
    private val bootstrapService: UvBootstrapService,
    private val processManager: MkdocsProcessManager,
    private val baseUrlDetector: BaseUrlDetector,
    private val previewPaneCoordinator: PreviewPaneCoordinator,
    private val errorPresenter: ActivationErrorPresenter,
) {
    /**
     * Activates plugin runtime for a project.
     *
     * @param projectId stable project key.
     * @param projectPath project root path.
     * @param startupOutput runtime startup stdout used for base URL detection.
     * @param featureFlags effective feature-flag policy.
     */
    fun activate(
        projectId: String,
        projectPath: String,
        startupOutput: String,
        featureFlags: FeatureFlagPolicy,
    ): ActivationResult {
        if (!featureFlags.allowsMvpFlow() || !featureFlags.disallowsFutureCycleFeatures()) {
            val reason = ActivationFailureReason.MVP_DISABLED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        val bootstrapResult = bootstrapService.bootstrap(projectPath)
        if (!bootstrapResult.success) {
            val reason = ActivationFailureReason.BOOTSTRAP_FAILED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, bootstrapResult.errorMessage),
            )
        }

        val startResult = processManager.start(
            projectId = projectId,
            workingDir = projectPath,
            config = RuntimeServerConfig(),
        )
        if (!startResult.started) {
            val reason = ActivationFailureReason.START_FAILED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        val baseUrl = baseUrlDetector.detectBaseUrl(startupOutput)
        if (baseUrl == null) {
            val reason = ActivationFailureReason.BASE_URL_NOT_FOUND
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        previewPaneCoordinator.open(projectId, baseUrl)
        return ActivationResult(
            success = true,
            previewUrl = baseUrl,
            message = "Activation completed",
        )
    }
}
