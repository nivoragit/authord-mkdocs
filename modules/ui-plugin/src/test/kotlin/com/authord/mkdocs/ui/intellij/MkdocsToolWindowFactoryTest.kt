package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.editor.impl.DocumentImpl
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class RecordingPreviewContent : PreviewContent {
    private val loaded = mutableListOf<String>()
    private val scrolled = mutableListOf<Int>()
    private val progress = mutableListOf<Double>()
    private val scrolledY = mutableListOf<Double>()
    private var snapshotCallback: ((PreviewDomSnapshot?) -> Unit)? = null

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

    override fun scrollToY(y: Double) {
        scrolledY += y
    }

    override fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        snapshotCallback = callback
    }

    fun loadedUrls(): List<String> = loaded.toList()

    fun scrolledDeltas(): List<Int> = scrolled.toList()

    fun scrolledProgressValues(): List<Double> = progress.toList()

    fun scrolledYValues(): List<Double> = scrolledY.toList()

    fun triggerSnapshot(snapshot: PreviewDomSnapshot?) {
        snapshotCallback?.invoke(snapshot)
    }
}

private class NoOpTopicTreeUiService : TopicTreeUiService {
    override fun dispatch(command: com.authord.mkdocs.ports.topic.TopicTreeCommand):
        com.authord.mkdocs.ports.topic.TopicGatewayResult<com.authord.mkdocs.ports.topic.TopicSyncOutcome> {
        return com.authord.mkdocs.ports.topic.TopicGatewayResult.Success(
            com.authord.mkdocs.ports.topic.TopicSyncOutcome(
                transactionId = command.commandId,
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "Applied",
            ),
        )
    }

    override fun refreshActiveTree():
        com.authord.mkdocs.ports.topic.TopicGatewayResult<com.authord.mkdocs.ports.topic.TopicSyncOutcome> {
        return com.authord.mkdocs.ports.topic.TopicGatewayResult.Success(
            com.authord.mkdocs.ports.topic.TopicSyncOutcome(
                transactionId = "refresh",
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "Refreshed",
            ),
        )
    }

    override fun selectInstance(instanceId: String):
        com.authord.mkdocs.ports.topic.TopicGatewayResult<com.authord.mkdocs.ports.topic.TopicInstanceRef> {
        return com.authord.mkdocs.ports.topic.TopicGatewayResult.Success(
            com.authord.mkdocs.ports.topic.TopicInstanceRef(
                instanceId = instanceId,
                configPath = "/tmp/project/mkdocs.yml",
                docsDirPath = "/tmp/project/docs",
            ),
        )
    }
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
    fun `create tool window content wires topic tree action and drag drop controllers`() {
        val project = IntellijTestFixtures.project()
        val fixture = IntellijTestFixtures.toolWindowFixture()
        val previewContent = RecordingPreviewContent()
        val runtimeService = PluginRuntimeIntegrationService(project)
        val uiService = NoOpTopicTreeUiService()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { runtimeService },
            previewContentFactory = { previewContent },
            topicTreeUiServiceResolver = { uiService },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        val controllers = factory.topicTreeControllers(project)
        assertNotNull(controllers)
    }

    @Test
    fun `tool window applies instance selection through switch coordinator`() {
        val project = IntellijTestFixtures.project()
        val fixture = IntellijTestFixtures.toolWindowFixture()
        val previewContent = RecordingPreviewContent()
        val runtimeService = PluginRuntimeIntegrationService(project)
        val uiService = NoOpTopicTreeUiService()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { runtimeService },
            previewContentFactory = { previewContent },
            topicTreeUiServiceResolver = { uiService },
        )
        factory.createToolWindowContent(project, fixture.toolWindow)

        val switchResult = factory.selectTopicTreeInstance(project, "default")

        assertNotNull(switchResult)
        assertTrue(switchResult is com.authord.mkdocs.ports.topic.TopicGatewayResult.Success)
    }

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
        assertEquals("Authord", panel.findLabel("Authord").text)
        assertTrue(panel.collectComponents().any { it is JButton })
        assertTrue(
            panel.collectComponents()
                .filterIsInstance<JButton>()
                .any { it.toolTipText == "Reload Configuration" },
        )
    }

    @Test
    fun `create tool window content includes topic tree component`() {
        val project = IntellijTestFixtures.project()
        val fixture = IntellijTestFixtures.toolWindowFixture()
        val previewContent = RecordingPreviewContent()
        val service = PluginRuntimeIntegrationService(project)
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        val panel = fixture.addedComponents.single() as JPanel
        assertTrue(panel.collectComponents().any { it is javax.swing.JTree })
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
    fun `scheduleScrollSync performs weighted percentage sync`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val delayedTasks = mutableListOf<() -> Unit>()
        // 4 lines, all text -> weight 1.0 each. Total 4.0.
        val document = DocumentImpl("Header 1\nContent\nHeader 2\nMore Content")
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { _, task ->
                delayedTasks.add(task)
            },
        )

        // Scroll to line 2 ("Header 2").
        // Weights before line 2: Line 0 (1.0) + Line 1 (1.0) = 2.0.
        // Total weight: 4.0.
        // Expected Progress: 2.0 / 4.0 = 0.5.
        factory.scheduleScrollSync(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
            topLine = 2,
            document = document,
            rawDelta = 80,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
        )

        // Verify flush task scheduled
        assertEquals(1, delayedTasks.size)
        delayedTasks.removeAt(0).invoke() // execute flush

        // Verify immediate scroll to 50%
        val progressValues = previewContent.scrolledProgressValues()
        assertEquals(1, progressValues.size)
        assertEquals(0.5, progressValues[0], 0.01)
    }

    @Test
    fun `scheduleScrollSync ignores non active editor path`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val document = DocumentImpl("one\ntwo\nthree\nfour")
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
            topLine = 0,
            document = document,
            rawDelta = 80,
            visibleStartOffset = 0,
            visibleEndOffset = 100,
        )

        assertFalse(scheduled)
        assertTrue(previewContent.scrolledProgressValues().isEmpty())
    }

    @Test
    fun `effective units assigns correct weights`() {
        val factory = MkdocsToolWindowFactory()
        val units = factory.computeEffectiveUnits(
            listOf(
                "# Title",                  // Weight 1
                "",                         // Weight 0 (Blank)
                "   ",                      // Weight 0 (Whitespace)
                "\\",                       // Weight 1 (Non-empty non-comment line)
                "[//]: # hidden",           // Weight 0 (Markdown comment)
                "<!-- html comment -->",    // Weight 1 (HTML comment treated as text)
                "Normal text",              // Weight 1
                "![img](foo.png)",          // Weight 1 (Image)
            ),
        )

        // total = 1 + 0 + 0 + 1 + 0 + 1 + 1 + 1 = 5
        assertEquals(5, units.total)
        assertEquals(1, units.units[0])
        assertEquals(0, units.units[1]) // Blank -> 0
        assertEquals(0, units.units[2]) // Whitespace -> 0
        assertEquals(1, units.units[3]) // Backslash -> 1
        assertEquals(0, units.units[4]) // Comment -> 0
        assertEquals(1, units.units[5]) // HTML comment -> 1
        assertEquals(1, units.units[6])
        assertEquals(1, units.units[7])
    }
}
