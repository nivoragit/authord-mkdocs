package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.intellij.openapi.actionSystem.ActionUpdateThread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartMkdocsActionPresentationTest {
    @Test
    fun `action update thread is background thread`() {
        val action = StartMkdocsAction()

        assertTrue(action.actionUpdateThread == ActionUpdateThread.BGT)
    }

    @Test
    fun `update hides action when no project is available`() {
        val action = StartMkdocsAction(
            runtimeServiceResolver = { error("Service lookup must not occur without project") },
        )
        val state = action.resolvePresentationState(project = null)

        assertFalse(state.visible)
        assertFalse(state.enabled)
    }

    @Test
    fun `update keeps action visible and enabled when runtime can start`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        val action = StartMkdocsAction(runtimeServiceResolver = { service })
        val state = action.resolvePresentationState(project)

        assertTrue(state.visible)
        assertTrue(state.enabled)
    }

    @Test
    fun `update keeps action enabled while preview runtime is already running`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        assertTrue(service.startPreview().success)
        assertTrue(service.isRuntimeRunning())

        val action = StartMkdocsAction(runtimeServiceResolver = { service })
        val state = action.resolvePresentationState(project)

        assertTrue(state.visible)
        assertTrue(state.enabled)
    }

    @Test
    fun `update disables action when feature policy blocks mvp execution`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.updateFeatureFlags(FeatureFlagPolicy(mvpEnabled = false))
        val action = StartMkdocsAction(runtimeServiceResolver = { service })
        val state = action.resolvePresentationState(project)

        assertTrue(state.visible)
        assertFalse(state.enabled)
    }

    @Test
    fun `update applies presentation flags through action event`() {
        val action = StartMkdocsAction(
            runtimeServiceResolver = { error("Service lookup must not occur without project") },
        )
        val event = IntellijTestFixtures.actionEvent(action, project = null)

        action.update(event)

        assertFalse(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
    }

    @Test
    fun `default resolver path reads runtime service from project container`() {
        val serviceHostProject = IntellijTestFixtures.project(locationHash = "service-host")
        val service = PluginRuntimeIntegrationService(serviceHostProject)
        val projectWithService = IntellijTestFixtures.project(
            locationHash = "service-container",
            services = mapOf(PluginRuntimeIntegrationService::class.java to service),
        )
        val action = StartMkdocsAction()

        val state = action.resolvePresentationState(projectWithService)

        assertTrue(state.visible)
        assertTrue(state.enabled)
    }
}
