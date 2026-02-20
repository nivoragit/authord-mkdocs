package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.Disposable
import com.intellij.ui.OnePixelSplitter
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.UvExecutableResult
import com.intellij.testFramework.LightVirtualFile
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JButton
import javax.swing.JPanel
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class RecordingPreviewContent : PreviewContent {
    private val loaded = mutableListOf<String>()
    private val scrolledY = mutableListOf<Double>()
    private val syncTokens = mutableListOf<Long?>()
    private var setupPageLoads: Int = 0
    private var setupProjectCreateHandler: ((String) -> Unit)? = null
    var domSnapshot: PreviewDomSnapshot? = null

    override val component: JComponent = JPanel()

    override fun loadUrl(url: String) {
        loaded += url
    }

    override fun loadSetupPage(onProjectCreate: (String) -> Unit) {
        setupPageLoads += 1
        setupProjectCreateHandler = onProjectCreate
    }

    override fun scrollToProgress(progress: Double) {
        // No-op in deterministic scroll tests.
    }

    override fun scrollToY(y: Double, syncToken: Long?) {
        scrolledY += y
        syncTokens += syncToken
    }

    override fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
        callback(domSnapshot)
    }

    override fun supportsPreviewEditorSync(): Boolean = true

    fun loadedUrls(): List<String> = loaded.toList()

    fun scrolledYValues(): List<Double> = scrolledY.toList()

    fun scrollSyncTokens(): List<Long?> = syncTokens.toList()

    fun setupPageLoadCount(): Int = setupPageLoads

    fun triggerSetupProjectCreate(projectName: String) {
        setupProjectCreateHandler?.invoke(projectName)
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

private fun waitUntil(timeoutMs: Long = 2_000L, check: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (check()) {
            return true
        }
        Thread.sleep(20)
    }
    return check()
}

private fun dispatchVfsEvents(
    listeners: List<BulkFileListener>,
    vararg events: VFileEvent,
) {
    if (listeners.isEmpty()) {
        return
    }
    listeners.forEach { listener ->
        listener.after(events.toMutableList())
    }
}

private fun triggerSetupPanelCreate(panel: SetupPanel) {
    val submitForm = panel::class.java.getDeclaredMethod("submitForm")
    submitForm.isAccessible = true
    submitForm.invoke(panel)
}

private fun createMkdocsConfigCreateEvent(projectRoot: Path): VFileEvent {
    return StubVFileEvent(projectRoot.resolve("mkdocs.yml").toString())
}

private fun createMkdocsConfigDeleteEvent(projectRoot: Path): VFileEvent {
    return StubVFileEvent(projectRoot.resolve("mkdocs.yml").toString())
}

private class StubVFileEvent(path: String) : VFileEvent(null) {
    private val normalizedPath = path.replace('\\', '/')
    private val virtualFile = object : LightVirtualFile(normalizedPath.substringAfterLast('/')) {
        override fun getPath(): String = normalizedPath
    }

    override fun computePath(): String = normalizedPath

    override fun getFile(): VirtualFile = virtualFile

    override fun getFileSystem(): VirtualFileSystem = virtualFile.fileSystem

    override fun isValid(): Boolean = true

    override fun hashCode(): Int = normalizedPath.hashCode()

    override fun equals(other: Any?): Boolean {
        return other is StubVFileEvent && other.normalizedPath == normalizedPath
    }
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
            mkdocsConfigPresenceResolver = { true },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        assertEquals(listOf(true), fixture.removedAllCalls)
        assertEquals(1, fixture.addedComponents.size)

        val panel = fixture.addedComponents.single() as JPanel
        assertTrue(
            panel.collectComponents().any { it.name == "authord-shell-toolbar" },
        )
        assertTrue(panel.collectComponents().any { it is JButton })
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
            mkdocsConfigPresenceResolver = { true },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        val panel = fixture.addedComponents.single() as JPanel
        assertTrue(panel.collectComponents().any { it is javax.swing.JTree })
    }

    @Test
    fun `create tool window content enters setup mode when mkdocs config is missing`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-setup-mode-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { false },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertFalse(splitter.secondComponent.isVisible)
            assertEquals(1.0f, splitter.proportion)
            assertEquals(1, previewContent.setupPageLoadCount())
            assertFalse(service.isRuntimeRunning())
            assertTrue(previewContent.loadedUrls().isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `authord treeview id enters setup mode when mkdocs config is missing`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-treeview-setup-mode-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture(toolWindowId = AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
            val service = PluginRuntimeIntegrationService(project)
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                mkdocsConfigPresenceResolver = { false },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val panel = fixture.addedComponents.single() as JPanel
            assertTrue(panel.collectComponents().none { it is OnePixelSplitter })
            assertTrue(panel.collectComponents().any { it is SetupPanel })
            assertFalse(panel.collectComponents().any { it is javax.swing.JTree })
            assertFalse(service.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `treeview setup mode keeps tree hidden until mkdocs config is created`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-treeview-setup-gate-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture(toolWindowId = AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
            val service = PluginRuntimeIntegrationService(project)
            val vfsListeners = mutableListOf<BulkFileListener>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                vfsBulkListenerRegistrar = { _, listener, _: Disposable ->
                    vfsListeners += listener
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val panel = fixture.addedComponents.single() as JPanel
            assertTrue(panel.collectComponents().any { it is SetupPanel })
            assertFalse(panel.collectComponents().any { it is javax.swing.JTree })
            assertFalse(service.isRuntimeRunning())

            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            dispatchVfsEvents(vfsListeners, createMkdocsConfigCreateEvent(projectRoot))

            assertTrue(
                waitUntil {
                    panel.collectComponents().any { it is javax.swing.JTree } &&
                        panel.collectComponents().none { it is SetupPanel }
                },
            )
            assertTrue(panel.collectComponents().none { it is OnePixelSplitter })
            assertTrue(waitUntil { service.isRuntimeRunning() })
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `treeview mode starts preview immediately when mkdocs config already exists`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-treeview-startup-existing-config-")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture(toolWindowId = AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
            val service = PluginRuntimeIntegrationService(project)
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                mkdocsConfigPresenceResolver = { true },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val panel = fixture.addedComponents.single() as JPanel
            assertTrue(panel.collectComponents().any { it is javax.swing.JTree })
            assertTrue(panel.collectComponents().none { it is SetupPanel })
            assertTrue(waitUntil { service.isRuntimeRunning() })
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `treeview setup create callback creates project then starts preview and shows tree`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-treeview-setup-create-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture(toolWindowId = AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val creator = MkDocsProjectCreator(
                commandRunner = { _, workingDir ->
                    val root = Path.of(workingDir)
                    Files.createDirectories(root.resolve("docs"))
                    Files.writeString(root.resolve("docs").resolve("index.md"), "# Welcome\n")
                    Files.writeString(root.resolve("mkdocs.yml"), "site_name: 'My Docs'\n")
                    CommandResult(exitCode = 0)
                },
                uvExecutableProvider = { UvExecutableResult(success = true, executablePath = "/tmp/uv") },
            )
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                mkDocsProjectCreatorResolver = { creator },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val panel = fixture.addedComponents.single() as JPanel
            val setupPanel = panel.collectComponents().filterIsInstance<SetupPanel>().firstOrNull()
            assertNotNull(setupPanel)
            triggerSetupPanelCreate(setupPanel)

            assertTrue(
                waitUntil {
                    service.isRuntimeRunning() &&
                        panel.collectComponents().any { it is javax.swing.JTree } &&
                        panel.collectComponents().none { it is SetupPanel }
                },
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scheduleTypingRefresh is skipped while tool window is in setup mode`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-setup-sync-guard-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                activeEditorPathProvider = { "${projectRoot}/docs/guide.md" },
                delayedInvoker = { _, task -> task() },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val scheduled = factory.scheduleTypingRefresh(
                project = project,
                previewContent = previewContent,
                selectedPath = "${projectRoot}/docs/guide.md",
            )

            assertFalse(scheduled)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setup mode does not register typing or scroll sync listeners`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-setup-listener-guard-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            var typingListenerRegistrations = 0
            var visibleAreaListenerRegistrations = 0
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { false },
                typingListenerRegistrar = { _, _, _ -> typingListenerRegistrations += 1 },
                visibleAreaListenerRegistrar = { _, _, _ -> visibleAreaListenerRegistrations += 1 },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            assertEquals(0, typingListenerRegistrations)
            assertEquals(0, visibleAreaListenerRegistrations)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preview mode registers typing and scroll sync listeners for sync capable preview`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-preview-listener-wiring-")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\n")
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            var typingListenerRegistrations = 0
            var visibleAreaListenerRegistrations = 0
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { true },
                typingListenerRegistrar = { _, _, _ -> typingListenerRegistrations += 1 },
                visibleAreaListenerRegistrar = { _, _, _ -> visibleAreaListenerRegistrations += 1 },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            assertEquals(1, typingListenerRegistrations)
            assertEquals(1, visibleAreaListenerRegistrations)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preview mode skips sync listener wiring for non sync capable preview`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-preview-listener-skip-")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\n")
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = object : PreviewContent {
                override val component: JComponent = JPanel()
                override fun loadUrl(url: String) = Unit
            }
            val service = PluginRuntimeIntegrationService(project)
            var typingListenerRegistrations = 0
            var visibleAreaListenerRegistrations = 0
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { true },
                typingListenerRegistrar = { _, _, _ -> typingListenerRegistrations += 1 },
                visibleAreaListenerRegistrar = { _, _, _ -> visibleAreaListenerRegistrations += 1 },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            assertEquals(0, typingListenerRegistrations)
            assertEquals(0, visibleAreaListenerRegistrations)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setup page create callback creates project then starts preview and shows tree`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-setup-create-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val creator = MkDocsProjectCreator(
                commandRunner = { _, workingDir ->
                    val root = Path.of(workingDir)
                    Files.createDirectories(root.resolve("docs"))
                    Files.writeString(root.resolve("docs").resolve("index.md"), "# Welcome\n")
                    Files.writeString(root.resolve("mkdocs.yml"), "site_name: 'My Docs'\n")
                    CommandResult(exitCode = 0)
                },
                uvExecutableProvider = { UvExecutableResult(success = true, executablePath = "/tmp/uv") },
            )
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                mkDocsProjectCreatorResolver = { creator },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)
            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertFalse(splitter.secondComponent.isVisible)

            previewContent.triggerSetupProjectCreate("demo-site")

            assertTrue(
                waitUntil {
                    service.isRuntimeRunning() &&
                        previewContent.loadedUrls().isNotEmpty() &&
                        splitter.secondComponent.isVisible
                },
            )
            assertTrue(Files.exists(projectRoot.resolve("mkdocs.yml")))
            val config = Files.readString(projectRoot.resolve("mkdocs.yml"))
            assertTrue(config.contains("site_name: 'demo-site'"))
            assertTrue(config.contains("docs_dir: docs"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `tool window switches to preview mode when mkdocs config is created externally`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-external-config-create-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val vfsListeners = mutableListOf<BulkFileListener>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                vfsBulkListenerRegistrar = { _, listener, _: Disposable ->
                    vfsListeners += listener
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)
            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertFalse(service.isRuntimeRunning())
            assertFalse(splitter.secondComponent.isVisible)
            assertEquals(1, previewContent.setupPageLoadCount())

            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )
            dispatchVfsEvents(vfsListeners, createMkdocsConfigCreateEvent(projectRoot))

            assertTrue(
                waitUntil {
                    service.isRuntimeRunning() &&
                        splitter.secondComponent.isVisible &&
                        previewContent.loadedUrls().isNotEmpty()
                },
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `initial preview startup refreshes open authord markdown previews after runtime is live`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-initial-startup-refresh-")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val refreshTriggers = mutableListOf<PreviewStartTrigger>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { true },
                markdownPreviewRefresher = { _, trigger ->
                    refreshTriggers += trigger
                    true
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            assertTrue(waitUntil { service.isRuntimeRunning() })
            assertTrue(waitUntil { refreshTriggers.contains(PreviewStartTrigger.TOOL_WINDOW) })
            assertEquals(listOf(PreviewStartTrigger.TOOL_WINDOW), refreshTriggers)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `tool window switches to preview mode on external config create with default cached resolver`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-external-config-create-default-cache-")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val vfsListeners = mutableListOf<BulkFileListener>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                vfsBulkListenerRegistrar = { _, listener, _: Disposable ->
                    vfsListeners += listener
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)
            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertFalse(service.isRuntimeRunning())
            assertFalse(splitter.secondComponent.isVisible)

            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            dispatchVfsEvents(vfsListeners, createMkdocsConfigCreateEvent(projectRoot))

            assertTrue(
                waitUntil {
                    service.isRuntimeRunning() &&
                        splitter.secondComponent.isVisible &&
                        previewContent.loadedUrls().isNotEmpty()
                },
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `tool window switches to setup mode when mkdocs config is deleted`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-external-config-delete-")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(
                projectRoot.resolve("mkdocs.yml"),
                """
                    site_name: Demo
                    docs_dir: docs
                """.trimIndent() + "\n",
            )
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val vfsListeners = mutableListOf<BulkFileListener>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                mkdocsConfigPresenceResolver = { Files.exists(projectRoot.resolve("mkdocs.yml")) },
                vfsBulkListenerRegistrar = { _, listener, _: Disposable ->
                    vfsListeners += listener
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)
            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertTrue(waitUntil { service.isRuntimeRunning() && splitter.secondComponent.isVisible })

            Files.delete(projectRoot.resolve("mkdocs.yml"))
            dispatchVfsEvents(vfsListeners, createMkdocsConfigDeleteEvent(projectRoot))

            assertTrue(
                waitUntil {
                    !service.isRuntimeRunning() &&
                        !splitter.secondComponent.isVisible &&
                        previewContent.setupPageLoadCount() >= 1
                },
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `tool window switches to setup mode on config delete with default cached resolver`() {
        val projectRoot = createTempDirectory(prefix = "tool-window-external-config-delete-default-cache-")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("docs").resolve("index.md"), "# Home\n")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val previewContent = RecordingPreviewContent()
            val service = PluginRuntimeIntegrationService(project)
            service.setStartupOutputForNextRun("ready at https://preview.example/")
            val vfsListeners = mutableListOf<BulkFileListener>()
            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { service },
                previewContentFactory = { previewContent },
                vfsBulkListenerRegistrar = { _, listener, _: Disposable ->
                    vfsListeners += listener
                },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)
            val panel = fixture.addedComponents.single() as JPanel
            val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
            assertNotNull(splitter)
            assertTrue(waitUntil { service.isRuntimeRunning() && splitter.secondComponent.isVisible })

            Files.delete(projectRoot.resolve("mkdocs.yml"))
            dispatchVfsEvents(vfsListeners, createMkdocsConfigDeleteEvent(projectRoot))

            assertTrue(
                waitUntil {
                    !service.isRuntimeRunning() &&
                        !splitter.secondComponent.isVisible &&
                        previewContent.setupPageLoadCount() >= 1
                },
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `restart plugin action restarts runtime and reloads preview`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)
        val routedUrl = service.navigateToSelectedFile("/tmp/project/docs/guides/index.md")
        assertTrue(routedUrl != null && routedUrl.endsWith("/guides/"))

        val previewContent = RecordingPreviewContent()
        val messages = mutableListOf<String>()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            resultPresenter = { _, message, _ -> messages += message },
            previewContentFactory = { previewContent },
        )

        val result = factory.restartPlugin(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
        )

        assertTrue(result.success)
        assertTrue(result.previewUrl.endsWith("/guides/"))
        assertEquals(1, previewContent.loadedUrls().size)
        assertTrue(previewContent.loadedUrls().single().endsWith("/guides/"))
        assertTrue(messages.last().contains("restarted", ignoreCase = true))
    }

    @Test
    fun `restart plugin refreshes topic tree state after successful restart`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        val previewContent = RecordingPreviewContent()
        var startupStateCalls = 0
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            mkdocsConfigPresenceResolver = { true },
            startupStateListener = { _, _ -> startupStateCalls += 1 },
        )
        val fixture = IntellijTestFixtures.toolWindowFixture()
        factory.createToolWindowContent(project, fixture.toolWindow)
        val baselineCalls = startupStateCalls

        val restartResult = factory.restartPlugin(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
        )

        assertTrue(restartResult.success)
        assertTrue(startupStateCalls > baselineCalls)
    }

    @Test
    fun `shell content uses resizable splitter when topic tree exists`() {
        val project = IntellijTestFixtures.project()
        val previewContent = RecordingPreviewContent()
        val topicTreeComponent = JPanel()
        val runtimeService = PluginRuntimeIntegrationService(project)
        val factory = MkdocsToolWindowFactory(
            previewContentFactory = { previewContent },
            mkdocsConfigPresenceResolver = { true },
        )

        val panel = factory.createShellContentPanel(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            topicTreeComponent = topicTreeComponent,
        ) as JPanel

        val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
        assertNotNull(splitter)
        assertTrue(splitter.proportion in 0.69f..0.71f)
        assertEquals(previewContent.component, splitter.firstComponent)
        assertEquals(topicTreeComponent, splitter.secondComponent)
    }

    @Test
    fun `shell layout mode switches between preview tree and combined views`() {
        val project = IntellijTestFixtures.project()
        val previewContent = RecordingPreviewContent()
        val topicTreeComponent = JPanel()
        val runtimeService = PluginRuntimeIntegrationService(project)
        val factory = MkdocsToolWindowFactory(
            previewContentFactory = { previewContent },
            mkdocsConfigPresenceResolver = { true },
        )
        val panel = factory.createShellContentPanel(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            topicTreeComponent = topicTreeComponent,
        ) as JPanel
        val splitter = panel.collectComponents().filterIsInstance<OnePixelSplitter>().firstOrNull()
        assertNotNull(splitter)
        val projectKey = runCatching { project.locationHash }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: project.name

        factory.setShellLayoutMode(projectKey, splitter, ShellLayoutMode.PREVIEW)
        assertFalse(topicTreeComponent.isVisible)
        assertTrue(previewContent.component.isVisible)
        assertEquals(1.0f, splitter.proportion)

        factory.setShellLayoutMode(projectKey, splitter, ShellLayoutMode.TREEVIEW)
        assertTrue(topicTreeComponent.isVisible)
        assertFalse(previewContent.component.isVisible)
        assertEquals(0.0f, splitter.proportion)

        factory.setShellLayoutMode(projectKey, splitter, ShellLayoutMode.PREVIEW_AND_TREEVIEW)
        assertTrue(previewContent.component.isVisible)
        assertTrue(topicTreeComponent.isVisible)
        assertTrue(splitter.proportion in 0.69f..0.71f)
    }

    @Test
    fun `shell content falls back to preview-only layout when topic tree is absent`() {
        val project = IntellijTestFixtures.project()
        val previewContent = RecordingPreviewContent()
        val runtimeService = PluginRuntimeIntegrationService(project)
        val factory = MkdocsToolWindowFactory(previewContentFactory = { previewContent })

        val panel = factory.createShellContentPanel(
            project = project,
            runtimeService = runtimeService,
            previewContent = previewContent,
            topicTreeComponent = null,
        ) as JPanel

        assertTrue(panel.collectComponents().none { it is OnePixelSplitter })
        assertTrue(panel.collectComponents().contains(previewContent.component))
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
            mkdocsConfigPresenceResolver = { true },
        )
        val fixture = IntellijTestFixtures.toolWindowFixture()

        factory.createToolWindowContent(project, fixture.toolWindow)

        assertTrue(service.isRuntimeRunning())
        assertEquals(1, previewContent.loadedUrls().size)
        val loadedRootUrl = previewContent.loadedUrls().single()
        assertTrue(loadedRootUrl.startsWith("http://127.0.0.1:"))
        assertTrue(loadedRootUrl.endsWith("/"))
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
        val factory = MkdocsToolWindowFactory(
            previewContentFactory = { previewContent },
            mkdocsConfigPresenceResolver = { true },
        )
        val fixture = IntellijTestFixtures.toolWindowFixture()

        factory.createToolWindowContent(projectWithService, fixture.toolWindow)

        assertTrue(service.isRuntimeRunning())
        assertEquals(1, previewContent.loadedUrls().size)
        val loadedRootUrl = previewContent.loadedUrls().single()
        assertTrue(loadedRootUrl.startsWith("http://127.0.0.1:"))
        assertTrue(loadedRootUrl.endsWith("/"))
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

        assertEquals(listOf(service.currentPreviewUrl()), previewContent.loadedUrls())
    }

    @Test
    fun `applyPreviewRoute updates preview route without reloading preview pane`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )

        val applied = factory.applyPreviewRoute(
            project = project,
            runtimeService = service,
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertTrue(applied)
        assertTrue(previewContent.loadedUrls().isEmpty())
        assertTrue(service.currentPreviewUrl()?.endsWith("/guide/") == true)
    }

    @Test
    fun `applyPreviewRoute ignores non docs selections`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
        )

        val applied = factory.applyPreviewRoute(
            project = project,
            runtimeService = service,
            selectedPath = "/tmp/project/README.md",
        )

        assertFalse(applied)
        assertTrue(previewContent.loadedUrls().isEmpty())
    }

    @Test
    fun `scheduleTypingRefresh saves document without reloading preview`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val delays = mutableListOf<Long>()
        val factory = MkdocsToolWindowFactory(
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { delay, task ->
                delays += delay
                task()
            },
        )

        val scheduled = factory.scheduleTypingRefresh(
            project = project,
            previewContent = RecordingPreviewContent(),
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertTrue(scheduled)
        assertEquals(listOf(2000L), delays)
    }

    @Test
    fun `scheduleTypingRefresh ignores non-active editor file`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val delays = mutableListOf<Long>()
        val factory = MkdocsToolWindowFactory(
            activeEditorPathProvider = { "/tmp/project/docs/other.md" },
            delayedInvoker = { delay, task ->
                delays += delay
                task()
            },
        )

        val scheduled = factory.scheduleTypingRefresh(
            project = project,
            previewContent = RecordingPreviewContent(),
            selectedPath = "/tmp/project/docs/guide.md",
        )

        assertFalse(scheduled)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `scheduleScrollSync performs deterministic y sync with token`() {
        val project = IntellijTestFixtures.project(basePath = "/tmp/project")
        val service = PluginRuntimeIntegrationService(project)
        service.setStartupOutputForNextRun("ready at https://preview.example/")
        assertTrue(service.startPreview().success)

        val previewContent = RecordingPreviewContent()
        previewContent.domSnapshot = PreviewDomSnapshot(
            maxScrollY = 1400.0,
            anchors = listOf(
                PreviewDomAnchor(id = "header-1", type = AnchorType.H, level = 1, top = 10.0, bottom = 50.0, normText = "header 1"),
                PreviewDomAnchor(id = "header-2", type = AnchorType.H, level = 2, top = 700.0, bottom = 740.0, normText = "header 2"),
            ),
        )
        val document = DocumentImpl("Header 1\nContent\nHeader 2\nMore Content")
        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { service },
            previewContentFactory = { previewContent },
            activeEditorPathProvider = { "/tmp/project/docs/guide.md" },
            delayedInvoker = { _, task -> task() },
        )

        factory.scheduleScrollSync(
            project = project,
            runtimeService = service,
            previewContent = previewContent,
            selectedPath = "/tmp/project/docs/guide.md",
            document = document,
            editorTopPx = 120.0,
            viewportHeightPx = 500.0,
            lineHeightPx = 20,
            rawDelta = 80,
        )

        assertTrue(previewContent.scrolledYValues().isNotEmpty())
        assertTrue(previewContent.scrolledYValues().first() >= 0.0)
        assertTrue(previewContent.scrollSyncTokens().first() != null)
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
            document = document,
            editorTopPx = 0.0,
            viewportHeightPx = 500.0,
            lineHeightPx = 20,
            rawDelta = 80,
        )

        assertFalse(scheduled)
        assertTrue(previewContent.scrolledYValues().isEmpty())
    }

    @Test
    fun `factory sync engine extraction supports mixed markdown anchors`() {
        val engine = MkdocsScrollSyncEngine(delayedInvoker = { _, _ -> })
        val source = engine.resolveSourceState(
            DocumentImpl(
                """
                # Title
                Intro paragraph line
                ![img](foo.png)
                ```kotlin
                println("x")
                ```
                """.trimIndent(),
            ),
        )

        assertTrue(source.anchors.any { it.type == AnchorType.H })
        assertTrue(source.anchors.any { it.type == AnchorType.IMG })
        assertTrue(source.anchors.any { it.type == AnchorType.CODE })
    }
}
