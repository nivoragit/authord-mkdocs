package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.runtime.BootstrapResult
import com.authord.mkdocs.ui.intellij.AuthordUiBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PluginActivationServiceErrorTest {

    @Test
    fun `test startFailureDetails returns git error message`() {
        // Stub dependencies
        val service = createServiceWithStubs()

        // Access private method
        val method = PluginActivationService::class.java.getDeclaredMethod("startFailureDetails", String::class.java, String::class.java)
        method.isAccessible = true

        val gitErrorOutput = """
            some random output
            git.exc.InvalidGitRepositoryError: /path/to/repo
            mkdocs_git_revision_date_localized_plugin
        """.trimIndent()

        val result = method.invoke(service, "/path/to/project", gitErrorOutput) as String
        val expected = AuthordUiBundle.message("activation.error.gitRequired")
        assertEquals(expected, result)
    }

    @Test
    fun `test startFailureDetails returns git command error message`() {
        val service = createServiceWithStubs()

        val method = PluginActivationService::class.java.getDeclaredMethod("startFailureDetails", String::class.java, String::class.java)
        method.isAccessible = true

        val gitErrorOutput = """
            some random output
            File "/lib/python3.13/site-packages/mkdocs_git_revision_date_localized_plugin/plugin.py", line 117, in on_config
            git.exc.GitCommandError: Cmd('git') failed due to: exit code(128)
        """.trimIndent()

        val result = method.invoke(service, "/path/to/project", gitErrorOutput) as String
        val expected = AuthordUiBundle.message("activation.error.gitRequired")
        assertEquals(expected, result)
    }

    @Test
    fun `test startFailureDetails returns module missing message`() {
        val service = createServiceWithStubs()

        val method = PluginActivationService::class.java.getDeclaredMethod("startFailureDetails", String::class.java, String::class.java)
        method.isAccessible = true

        val moduleErrorOutput = """
            Traceback (most recent call last):
            ModuleNotFoundError: No module named 'mkdocs-material'
        """.trimIndent()

        val result = method.invoke(service, "/path/to/project", moduleErrorOutput) as String
        val expected = AuthordUiBundle.message("activation.error.moduleMissing", moduleErrorOutput)
        assertEquals(expected, result)
    }

    private fun createServiceWithStubs(): PluginActivationService {
        // Create dummy stub instances for dependencies since we only test a private pure method
        // that doesn't use them (startFailureDetails doesn't use class fields in the paths we test)
        
        // We need to pass nulls or simple objects where possible, but Kotlin non-null types require valid instances.
        // We can use Mockito if available, or just create simple anonymous subclasses.
        // Assuming we don't know if Mockito is there, let's try to pass nulls with unchecked cast or create dummy objects.
        
        // Actually, let's just use reflection to avoid constructing the complex object if possible? 
        // No, we need an instance to invoke non-static method.
        // Let's rely on the fact that startFailureDetails only uses arguments.
        // We can construct the service with "null" cast to non-null types if we are careful not to call methods that use them.
        
        return PluginActivationService(
            bootstrapService = StubUvBootstrapService(),
            processManager = StubProcessManager(),
            baseUrlDetector = StubBaseUrlDetector(),
            previewPaneCoordinator = StubPreviewPaneCoordinator(),
            errorPresenter = StubErrorPresenter()
        )
    }
    
    // Stubs
    class StubUvBootstrapService : UvBootstrapService({ _, _ -> com.authord.mkdocs.runtime.CommandResult(0) }) {
        override fun bootstrap(projectPath: String): BootstrapResult = 
            BootstrapResult(true, "", "uv", emptyList(), false, "")
    }

    class StubProcessManager : MkdocsProcessManager({ _, _ ->
        object : com.authord.mkdocs.runtime.ManagedProcessHandle {
            override val id = "test"
            override fun stop() {}
            override fun isAlive() = true
        }
    }) {
        override fun start(projectId: String, workingDir: String, config: com.authord.mkdocs.runtime.RuntimeServerConfig): com.authord.mkdocs.runtime.RuntimeStartResult {
            // Note: RuntimeStartResult was renamed or different in signature?
            // Let's check imports in the test file... 
            // Wait, I saw RuntimeStartResult in the file view.
            return com.authord.mkdocs.runtime.RuntimeStartResult(true, "pid", emptyList(), false, "")
        }
        override fun stop(projectId: String): Boolean = true
    }
    
    class StubBaseUrlDetector : BaseUrlDetector() {
        override fun detectBaseUrl(startupOutput: String): String? = "http://localhost:8000"
    }
    
    class StubPreviewPaneCoordinator : PreviewPaneCoordinator() {
        override fun open(projectId: String, baseUrl: String): PreviewPaneState {
            return PreviewPaneState(projectId, baseUrl, "/", true)
        }
    }
    
    class StubErrorPresenter : ActivationErrorPresenter() {
        override fun present(reason: ActivationFailureReason, details: String): String = ""
    }
}
