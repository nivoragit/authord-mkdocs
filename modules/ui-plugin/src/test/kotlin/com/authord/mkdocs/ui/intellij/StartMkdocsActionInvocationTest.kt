package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartMkdocsActionInvocationTest {
    @Test
    fun `actionPerformed is no-op when project context is missing`() {
        val action = StartMkdocsAction(
            runtimeServiceResolver = { error("Service must not be resolved without project context") },
        )

        val invoked = action.invokeForProject(project = null)

        assertFalse(invoked)
    }

    @Test
    fun `actionPerformed delegates start request to runtime integration service`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        val action = StartMkdocsAction(runtimeServiceResolver = { service })

        val invoked = action.invokeForProject(project)

        assertTrue(invoked)
        assertTrue(service.isRuntimeRunning())
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun `actionPerformed remains invokable while runtime is already running`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        val messages = mutableListOf<String>()
        val action = StartMkdocsAction(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
        )

        val first = action.invokeForProject(project)
        val second = action.invokeForProject(project)

        assertTrue(first)
        assertTrue(second)
        assertTrue(service.isRuntimeRunning())
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))
        assertEquals(2, messages.size)
        assertTrue(messages.last().contains("already running", ignoreCase = true))
    }

    @Test
    fun `actionPerformed does not start runtime when action is not startable`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.updateFeatureFlags(FeatureFlagPolicy(mvpEnabled = false))
        val action = StartMkdocsAction(runtimeServiceResolver = { service })

        val invoked = action.invokeForProject(project)

        assertFalse(invoked)
        assertFalse(service.isRuntimeRunning())
    }

    @Test
    fun `actionPerformed blocks start when compatibility release gate is blocked`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        val action = StartMkdocsAction(
            runtimeServiceResolver = { service },
            compatibilityGateServiceResolver = { CompatibilityReleaseGateService() },
            compatibilityRegressionsProvider = {
                listOf(
                    CompatibilityRegression(
                        id = "compat-1",
                        kind = CompatibilityRegressionKind.PREVIEW_START_FAILURE,
                        description = "Preview does not start",
                        resolved = false,
                    ),
                )
            },
        )

        val invoked = action.invokeForProject(project)

        assertFalse(invoked)
        assertFalse(service.isRuntimeRunning())
    }

    @Test
    fun `actionPerformed entrypoint handles null project event`() {
        val action = StartMkdocsAction(
            runtimeServiceResolver = { error("Service must not be resolved without project context") },
        )
        val event = IntellijTestFixtures.actionEvent(action, project = null)

        action.actionPerformed(event)

        assertFalse(action.invokeForProject(project = null))
    }

    @Test
    fun `actionPerformed async branch invokes success callback`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        val messages = mutableListOf<String>()
        var successfulStartCallbackInvoked = false
        val action = StartMkdocsAction(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
            isApplicationAvailable = { true },
        )

        val invoked = action.invokeForProject(project) {
            successfulStartCallbackInvoked = true
        }

        assertTrue(invoked)
        assertTrue(successfulStartCallbackInvoked)
        assertTrue(messages.isNotEmpty())
        assertTrue(service.isRuntimeRunning())
    }
}
