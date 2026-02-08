package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ActivationHandle(
    override val id: String,
    private val alive: Boolean,
) : ManagedProcessHandle {
    override fun stop() {
        // no-op
    }

    override fun isAlive(): Boolean = alive
}

class PluginActivationServiceTest {
    private fun service(
        bootstrap: UvBootstrapService,
        processManager: MkdocsProcessManager,
    ): PluginActivationService {
        return PluginActivationService(
            bootstrapService = bootstrap,
            processManager = processManager,
            baseUrlDetector = BaseUrlDetector(),
            previewPaneCoordinator = PreviewPaneCoordinator(),
            errorPresenter = ActivationErrorPresenter(),
        )
    }

    @Test
    fun `fails when mvp flow is disabled`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(mvpEnabled = false),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.MVP_DISABLED, result.reason)
    }

    @Test
    fun `fails when bootstrap fails`() {
        val svc = service(
            bootstrap = UvBootstrapService { command, _ ->
                if (command[1] == "venv") CommandResult(1, stderr = "boom") else CommandResult(0)
            },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.BOOTSTRAP_FAILED, result.reason)
        assertTrue(result.message.contains("boom"))
    }

    @Test
    fun `fails when process start returns not started`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", false) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.START_FAILED, result.reason)
    }

    @Test
    fun `fails when base url cannot be parsed`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "no url here",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.BASE_URL_NOT_FOUND, result.reason)
    }

    @Test
    fun `succeeds when bootstrap start and url detection succeed`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", true) }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "ready at http://127.0.0.1:8000/",
            featureFlags = FeatureFlagPolicy(),
        )

        assertTrue(result.success)
        assertEquals("http://127.0.0.1:8000/", result.previewUrl)
        assertEquals("Activation completed", result.message)
    }
}
