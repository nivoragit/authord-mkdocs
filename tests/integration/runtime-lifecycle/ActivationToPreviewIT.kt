package integration.runtime

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ui.ActivationErrorPresenter
import com.authord.mkdocs.ui.PluginActivationService
import com.authord.mkdocs.ui.PreviewPaneCoordinator
import kotlin.test.Test
import kotlin.test.assertTrue

private class IntegrationHandle(override val id: String) : ManagedProcessHandle {
    private var alive = true

    override fun stop() {
        alive = false
    }

    override fun isAlive(): Boolean = alive
}

class ActivationToPreviewIT {
    @Test
    fun `activation starts runtime and opens preview`() {
        val bootstrap = UvBootstrapService { _, _ -> CommandResult(0) }
        val processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> IntegrationHandle("runtime-1") })
        val preview = PreviewPaneCoordinator()
        val service = PluginActivationService(
            bootstrapService = bootstrap,
            processManager = processManager,
            baseUrlDetector = BaseUrlDetector(),
            previewPaneCoordinator = preview,
            errorPresenter = ActivationErrorPresenter(),
        )

        val result = service.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "Server listening at http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertTrue(result.success)
        assertTrue(preview.currentUrl("project-1")!!.startsWith("http://127.0.0.1:8000"))
    }
}
