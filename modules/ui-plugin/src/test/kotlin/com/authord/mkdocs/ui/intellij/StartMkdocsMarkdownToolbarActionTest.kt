package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.actionSystem.ActionUpdateThread
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartMkdocsMarkdownToolbarActionTest {
    @Test
    fun `toolbar action update thread is background thread`() {
        val action = StartMkdocsMarkdownToolbarAction()

        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }

    @Test
    fun `update is visible and enabled in markdown context when preview can start`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        val delegate = StartMkdocsAction(runtimeServiceResolver = { service })
        val action = StartMkdocsMarkdownToolbarAction(delegate = delegate)
        val event = IntellijTestFixtures.actionEvent(
            action = action,
            project = project,
            virtualFilePath = "/tmp/project/docs/index.md",
        )

        action.update(event)

        assertTrue(event.presentation.isVisible)
        assertTrue(event.presentation.isEnabled)
    }

    @Test
    fun `update is hidden and disabled in non-markdown context`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        val delegate = StartMkdocsAction(runtimeServiceResolver = { service })
        val action = StartMkdocsMarkdownToolbarAction(delegate = delegate)
        val event = IntellijTestFixtures.actionEvent(
            action = action,
            project = project,
            virtualFilePath = "/tmp/project/README.txt",
        )

        action.update(event)

        assertFalse(event.presentation.isVisible)
        assertFalse(event.presentation.isEnabled)
    }

    @Test
    fun `actionPerformed delegates to existing start pipeline for markdown context`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/docs/")
        val delegate = StartMkdocsAction(runtimeServiceResolver = { service })
        var toolWindowOpened = 0
        val action = StartMkdocsMarkdownToolbarAction(delegate = delegate)
        val event = IntellijTestFixtures.actionEvent(
            action = action,
            project = project,
            virtualFilePath = "/tmp/project/docs/index.md",
        )

        val actionWithOpener = StartMkdocsMarkdownToolbarAction(
            delegate = delegate,
            toolWindowOpener = { toolWindowOpened += 1 },
        )
        actionWithOpener.actionPerformed(event)

        assertTrue(service.isRuntimeRunning())
        val currentUrl = service.currentPreviewUrl()
        assertTrue(currentUrl != null && currentUrl.startsWith("http://127.0.0.1:"))
        assertEquals(1, toolWindowOpened)
    }

    @Test
    fun `actionPerformed is no-op outside markdown context`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        val delegate = StartMkdocsAction(runtimeServiceResolver = { service })
        var toolWindowOpened = 0
        val action = StartMkdocsMarkdownToolbarAction(delegate = delegate)
        val event = IntellijTestFixtures.actionEvent(
            action = action,
            project = project,
            virtualFilePath = "/tmp/project/README.txt",
        )

        val actionWithOpener = StartMkdocsMarkdownToolbarAction(
            delegate = delegate,
            toolWindowOpener = { toolWindowOpened += 1 },
        )
        actionWithOpener.actionPerformed(event)

        assertFalse(service.isRuntimeRunning())
        assertEquals(0, toolWindowOpened)
    }

    @Test
    fun `actionPerformed opens authord setup mode but does not start runtime when mkdocs config is missing`() {
        val projectRoot = Files.createTempDirectory("authord-markdown-toolbar-no-config-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val service = PluginRuntimeIntegrationService(project)
            var toolWindowOpened = 0
            val delegate = StartMkdocsAction(runtimeServiceResolver = { service })
            val action = StartMkdocsMarkdownToolbarAction(
                delegate = delegate,
                toolWindowOpener = { toolWindowOpened += 1 },
            )
            val event = IntellijTestFixtures.actionEvent(
                action = action,
                project = project,
                virtualFilePath = projectRoot.resolve("docs").resolve("index.md").toString(),
            )

            action.actionPerformed(event)

            assertEquals(1, toolWindowOpened)
            assertFalse(service.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
