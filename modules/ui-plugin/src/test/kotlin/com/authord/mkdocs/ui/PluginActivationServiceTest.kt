package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.ManagedProcessHandle
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.ProcessLauncher
import com.authord.mkdocs.runtime.UvBootstrapService
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class ActivationHandle(
    override val id: String,
    private var alive: Boolean,
    private val startupOutputText: String = "",
) : ManagedProcessHandle {
    var stopCalls: Int = 0
        private set

    override fun stop() {
        stopCalls += 1
        alive = false
    }

    override fun isAlive(): Boolean = alive

    override fun startupOutput(): String = startupOutputText
}

class PluginActivationServiceTest {
    private class RecordingStartLauncher(
        private val handle: ManagedProcessHandle,
    ) : ProcessLauncher {
        var command: List<String> = emptyList()
            private set
        var workingDir: String = ""
            private set

        override fun launch(command: List<String>, workingDir: String): ManagedProcessHandle {
            this.command = command
            this.workingDir = workingDir
            return handle
        }
    }

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
    fun `includes startup output details when process start fails`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ ->
                ActivationHandle(
                    id = "p1",
                    alive = false,
                    startupOutputText = "Error: Config file 'mkdocs.yml' does not exist.",
                )
            }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.START_FAILED, result.reason)
        assertTrue(result.message.contains("Config file 'mkdocs.yml' does not exist"))
    }

    @Test
    fun `includes mkdocs config hint when process start fails without output`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-missing-config-")
        try {
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> ActivationHandle("p1", false) }),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertFalse(result.success)
            assertEquals(ActivationFailureReason.START_FAILED, result.reason)
            assertTrue(result.message.contains("No mkdocs.yml or mkdocs.yaml found in project root"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fails when base url cannot be parsed`() {
        val handle = ActivationHandle("p1", true)
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ -> handle }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "no url here",
            featureFlags = FeatureFlagPolicy(),
        )

        assertFalse(result.success)
        assertEquals(ActivationFailureReason.BASE_URL_NOT_FOUND, result.reason)
        assertEquals(1, handle.stopCalls)
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

    @Test
    fun `detects preview url from process startup output when explicit startup output is empty`() {
        val svc = service(
            bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
            processManager = MkdocsProcessManager(ProcessLauncher { _, _ ->
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/")
            }),
        )

        val result = svc.activate(
            projectId = "project-1",
            projectPath = "/tmp/project",
            startupOutput = "",
            featureFlags = FeatureFlagPolicy(),
        )

        assertTrue(result.success)
        assertEquals("http://127.0.0.1:8000/", result.previewUrl)
    }

    @Test
    fun `uses parent guard script command for mkdocs runtime start`() {
        val projectRoot = createTempDirectory(prefix = "plugin-activation-parent-guard-")
        try {
            val launcher = RecordingStartLauncher(
                ActivationHandle("p1", alive = true, startupOutputText = "Serving at http://127.0.0.1:8000/"),
            )
            val svc = service(
                bootstrap = UvBootstrapService { _, _ -> CommandResult(0) },
                processManager = MkdocsProcessManager(launcher),
            )

            val result = svc.activate(
                projectId = "project-1",
                projectPath = projectRoot.toString(),
                startupOutput = "",
                featureFlags = FeatureFlagPolicy(),
            )

            assertTrue(result.success)
            assertEquals(projectRoot.toString(), launcher.workingDir)
            val expectedRuntimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()
            assertTrue(launcher.command.take(5) == listOf("uv", "run", "--python", expectedRuntimePath, "python"))
            assertTrue("--parent-pid" in launcher.command)
            assertTrue("--working-dir" in launcher.command)
            assertTrue("serve" in launcher.command)
            assertTrue("--livereload" in launcher.command)
            assertTrue("--dirty" in launcher.command)
            assertTrue("--dirtyreload" !in launcher.command)

            val scriptPath = projectRoot.resolve(".mkdocs-plugin-runtime").resolve("serve_with_parent_guard.py")
            assertTrue(Files.exists(scriptPath))
            val script = Files.readString(scriptPath)
            assertTrue(script.contains("def parent_alive"))
            assertTrue(script.contains("if not parent_alive"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
