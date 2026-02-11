package com.authord.mkdocs.ui.intellij

import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingPreviewContent : PreviewContent {
    private val loaded = mutableListOf<String>()
    private val scrolled = mutableListOf<Int>()
    private val progress = mutableListOf<Double>()

    override val component: JComponent = JPanel()

    override fun loadUrl(url: String) {
        loaded += url
    }

    override fun scrollBy(delta: Int) {
        scrolled += delta
    }

    override fun scrollToProgress(progress: Double) {
        this.progress += progress
    }

    fun loadedUrls(): List<String> = loaded.toList()

    fun scrolledDeltas(): List<Int> = scrolled.toList()

    fun scrolledProgressValues(): List<Double> = progress.toList()
}

private fun JPanel.findLabel(text: String): JLabel {
    return collectComponents()
        .filterIsInstance<JLabel>()
        .first { it.text == text }
}

private fun JPanel.collectComponents(): List<java.awt.Component> {
    val collected = mutableListOf<java.awt.Component>()
    fun visit(component: java.awt.Component) {
        collected += component
        if (component is java.awt.Container) {
            component.components.forEach { child -> visit(child) }
        }
    }
    visit(this)
    return collected
}

class MkdocsToolWindowFactoryTest {
    @Test
    fun `create tool window content registers minimal shell panel`() {
        val project = IntellijTestFixtures.project()
        val fixture = IntellijTestFixtures.toolWindowFixture()
        val previewContent = RecordingPreviewContent()
        val service = PluginRuntimeIntegrationService(project)
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        assertEquals(listOf(true), fixture.removedAllCalls)
        assertEquals(1, fixture.addedComponents.size)

        val panel = fixture.addedComponents.single() as JPanel
        assertEquals("MkDocs Preview Shell", panel.findLabel("MkDocs Preview Shell").text)
        assertTrue(panel.collectComponents().none { it is javax.swing.JButton })
    }

    @Test
    fun `tool window is available for all project contexts`() {
        val project = IntellijTestFixtures.project(basePath = null)
        val factory = MkdocsToolWindowFactory()

        assertTrue(factory.shouldBeAvailable(project))
    }

    @Test
    fun `tool window auto-starts preview when project can start preview`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )
        val fixture = IntellijTestFixtures.toolWindowFixture()

        factory.createToolWindowContent(project, fixture.toolWindow)

        assertTrue(service.isRuntimeRunning())
        assertEquals(listOf("https://preview.example/"), previewContent.loadedUrls())
    }

    @Test
    fun `tool window does not load preview when runtime cannot start`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.updateFeatureFlags(com.authord.mkdocs.core.flags.FeatureFlagPolicy(mvpEnabled = false))
        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )
        val fixture = IntellijTestFixtures.toolWindowFixture()

        factory.createToolWindowContent(project, fixture.toolWindow)

        assertFalse(service.isRuntimeRunning())
        assertTrue(previewContent.loadedUrls().isEmpty())
    }

    @Test
    fun `default resolver path reads runtime service from project container`() {
        val serviceHostProject = IntellijTestFixtures.project(locationHash = "factory-service-host")
        val service = PluginRuntimeIntegrationService(serviceHostProject)
        service.setStartupOutputForNextRun("ready at https://preview.example/default/")
        val projectWithService = IntellijTestFixtures.project(
            locationHash = "factory-service-container",
            services = mapOf(PluginRuntimeIntegrationService::class.java to service),
        )
        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(previewContentFactory = { previewContent })
        val fixture = IntellijTestFixtures.toolWindowFixture()

        factory.createToolWindowContent(projectWithService, fixture.toolWindow)

        assertTrue(service.isRuntimeRunning())
        assertEquals(listOf("https://preview.example/default/"), previewContent.loadedUrls())
    }

    @Test
    fun `tool window loads existing preview url on panel creation`() {
        val project = IntellijTestFixtures.project()
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/preloaded/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )

        factory.createShellContentPanel(project)

        assertEquals(listOf("https://preview.example/preloaded/"), previewContent.loadedUrls())
    }

    @Test
    fun `applyPreviewRoute updates preview url for docs markdown selection`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val messages = mutableListOf<String>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
            previewContentFactory = { previewContent },
        )

        val applied = factory.applyPreviewRoute(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertTrue(applied)
        assertEquals(listOf("https://preview.example/guide/"), previewContent.loadedUrls())
        assertTrue(messages.last().contains("updated", ignoreCase = true))
    }

    @Test
    fun `applyPreviewRoute ignores non docs selections`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val messages = mutableListOf<String>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
            previewContentFactory = { previewContent },
        )

        val applied = factory.applyPreviewRoute(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/README.md",
        )

        assertFalse(applied)
        assertTrue(previewContent.loadedUrls().isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `refreshCurrentPreviewForDocsSave forces reload for docs markdown when runtime is active`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val messages = mutableListOf<String>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
            previewContentFactory = { previewContent },
        )

        val refreshed = factory.refreshCurrentPreviewForDocsSave(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertTrue(refreshed)
        assertEquals(listOf("https://preview.example/"), previewContent.loadedUrls())
        assertTrue(messages.last().contains("refreshed", ignoreCase = true))
    }

    @Test
    fun `scheduleTypingRefresh updates preview for active docs markdown file`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val delays = mutableListOf<Long>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { delay, task ->
                delays += delay
                task()
            },
        )

        val scheduled = factory.scheduleTypingRefresh(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertTrue(scheduled)
        assertEquals(listOf(450L), delays)
        assertEquals(
            listOf("https://preview.example/guide/"),
            previewContent.loadedUrls(),
        )
    }

    @Test
    fun `scheduleTypingRefresh ignores non-active editor file`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val delays = mutableListOf<Long>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/other.md" },
            delayedInvoker = { delay, task ->
                delays += delay
                task()
            },
        )

        val scheduled = factory.scheduleTypingRefresh(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertFalse(scheduled)
        assertTrue(delays.isEmpty())
        assertTrue(previewContent.loadedUrls().isEmpty())
    }

    @Test
    fun `scheduleScrollSync applies editor viewport percentage to preview`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val delays = mutableListOf<Long>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { delay, task ->
                delays += delay
                task()
            },
        )

        val scheduled = factory.scheduleScrollSync(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
            rawDelta = 120,
            documentLength = 1000,
            visibleStartOffset = 400,
            visibleEndOffset = 600,
        )

        assertTrue(scheduled)
        assertEquals(listOf(20L), delays)
        assertEquals(1, previewContent.scrolledProgressValues().size)
        assertEquals(0.5, previewContent.scrolledProgressValues().single(), 0.0001)
    }

    @Test
    fun `scheduleScrollSync clamps to bottom progress near end of document`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { _, task -> task() },
        )

        val scheduled = factory.scheduleScrollSync(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
            rawDelta = 100,
            documentLength = 1000,
            visibleStartOffset = 900,
            visibleEndOffset = 1000,
        )

        assertTrue(scheduled)
        assertEquals(1, previewContent.scrolledProgressValues().size)
        assertEquals(1.0, previewContent.scrolledProgressValues().single(), 0.0001)
    }

    @Test
    fun `scheduleScrollSync ignores non active editor path`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/other.md" },
            delayedInvoker = { _, task -> task() },
        )

        val scheduled = factory.scheduleScrollSync(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
            rawDelta = 80,
            documentLength = 1000,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
        )

        assertFalse(scheduled)
        assertTrue(previewContent.scrolledProgressValues().isEmpty())
    }
}
