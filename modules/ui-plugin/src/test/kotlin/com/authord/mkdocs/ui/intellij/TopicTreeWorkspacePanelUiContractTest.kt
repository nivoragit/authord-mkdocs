package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicTreeWorkspacePanelUiContractTest {
    @Test
    fun `tooltips use exact contract strings`() {
        val panel = panelWithDefaults()

        val tooltips = panel.tooltipTextsForTest().toSet()

        listOf(
            "New Topic",
            "Expand All",
            "Collapse All",
            "Synchronize TOC and Editor",
        ).forEach { expected ->
            assertTrue(tooltips.contains(expected), "Missing tooltip '$expected'")
        }
    }

    @Test
    fun `header actions follow required toc order`() {
        val panel = panelWithDefaults()

        assertEquals(
            listOf("New Topic", "Expand All", "Collapse All", "Synchronize TOC and Editor"),
            panel.headerActionTooltipsForTest(),
        )
    }

    @Test
    fun `menu structure matches contract order`() {
        val panel = panelWithDefaults()
        assertEquals(
            listOf("New Child Topic", "Edit Title", "Remove TOC Element"),
            panel.tocContextMenuLabelsForTest(),
        )
    }

    @Test
    fun `enablement follows capability and selection rules`() {
        val panel = panelWithDefaults()
        panel.render(sampleState())

        val tocWithoutSelection = panel.tocContextActionStatesForTest()
        assertFalse(tocWithoutSelection.getValue("Child"))
        assertFalse(tocWithoutSelection.getValue("Delete"))

        assertTrue(panel.selectTreeNodeForTest("n1"))
        val tocWithSelection = panel.tocContextActionStatesForTest()
        assertTrue(tocWithSelection.getValue("Child"))
        assertTrue(tocWithSelection.getValue("Delete"))

        val headerVisibility = panel.headerActionVisibilityForTest()
        assertTrue(headerVisibility.getValue("Expand All"))
        assertTrue(headerVisibility.getValue("Collapse All"))
        assertTrue(headerVisibility.getValue("New Topic"))
        assertTrue(headerVisibility.getValue("Synchronize TOC and Editor"))
    }

    @Test
    fun `expand all header action publishes expanded status`() {
        val panel = panelWithDefaults()
        panel.render(sampleState())

        val triggered = panel.triggerHeaderActionForTest("Expand All")

        assertTrue(triggered)
        assertEquals("Expanded all topics", panel.statusTextForTest())
    }

    @Test
    fun `collapse all header action collapses first visible toc node`() {
        val panel = panelWithDefaults()
        panel.render(sampleNavConvertedSectionState())
        assertTrue(panel.triggerHeaderActionForTest("Expand All"))
        assertTrue(panel.isTreeNodeExpandedForTest("child"))

        val triggered = panel.triggerHeaderActionForTest("Collapse All")

        assertTrue(triggered)
        assertFalse(panel.isTreeNodeExpandedForTest("child"))
        assertEquals("Collapsed all topics", panel.statusTextForTest())
    }

    @Test
    fun `sync toc and editor header action reports unavailable without project`() {
        val panel = panelWithDefaults()

        val triggered = panel.triggerHeaderActionForTest("Synchronize TOC and Editor")

        assertTrue(triggered)
        assertEquals("Synchronize TOC and Editor is unavailable", panel.statusTextForTest())
    }

    @Test
    fun `toc remove action dispatches through controller pipeline`() {
        val uiService = RecordingUiService()
        val panel = panelWithDefaults(uiService = uiService)
        panel.render(sampleState())
        assertTrue(panel.selectTreeNodeForTest("n1"))

        val triggered = panel.triggerTocContextActionForTest("Remove TOC Element")

        assertTrue(triggered)
        assertTrue(uiService.dispatched.any { it is RemoveTopicNodeCommand })
    }

    @Test
    fun `new child topic auto-generates markdown path when input left blank`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Install Guide", ""))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
        )
        panel.render(sampleState())
        assertTrue(panel.selectTreeNodeForTest("n2"))

        val triggered = panel.triggerTocContextActionForTest("New Child Topic")

        assertTrue(triggered)
        val command = uiService.dispatched.filterIsInstance<AddChildTopicNodeCommand>().last()
        assertEquals("guide/install-guide.md", command.childSourcePath)
    }

    @Test
    fun `new root child topic auto-generates markdown path when input left blank`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Getting Started", ""))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
        )
        panel.render(sampleState())
        assertTrue(panel.selectTreeNodeForTest("root"))

        val triggered = panel.triggerTocContextActionForTest("New Child Topic")

        assertTrue(triggered)
        val command = uiService.dispatched.filterIsInstance<AddChildTopicNodeCommand>().last()
        assertEquals("getting-started.md", command.childSourcePath)
    }

    @Test
    fun `fallback child topic auto-generates markdown path when input left blank`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Advanced", ""))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
        )
        panel.render(sampleFallbackState())
        assertTrue(panel.selectTreeNodeForTest("page:install.md"))

        val triggered = panel.triggerTocContextActionForTest("New Child Topic")

        assertTrue(triggered)
        val command = uiService.dispatched.filterIsInstance<AddChildTopicNodeCommand>().last()
        assertEquals("install/advanced.md", command.childSourcePath)
    }

    @Test
    fun `new root topic action auto-generates markdown path when input left blank`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Getting Started", ""))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
        )
        panel.render(sampleState())

        val triggered = panel.triggerHeaderActionForTest("New Topic")

        assertTrue(triggered)
        val command = uiService.dispatched.filterIsInstance<AddTopicNodeCommand>().last()
        assertEquals("getting-started.md", command.sourcePath)
    }

    @Test
    fun `duplicate markdown path suggests incremented filename and uses it on confirm`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Index", ""))
        val duplicatePrompts = mutableListOf<Pair<String, String>>()
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
            duplicatePathPrompt = { requested, suggested ->
                duplicatePrompts += requested to suggested
                true
            },
        )
        panel.render(sampleState())

        val triggered = panel.triggerHeaderActionForTest("New Topic")

        assertTrue(triggered)
        val command = uiService.dispatched.filterIsInstance<AddTopicNodeCommand>().last()
        assertEquals("index-2.md", command.sourcePath)
        assertEquals(listOf("index.md" to "index-2.md"), duplicatePrompts)
    }

    @Test
    fun `duplicate markdown path cancels create when suggestion is rejected`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Index", ""))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
            duplicatePathPrompt = { _, _ -> false },
        )
        panel.render(sampleState())

        val triggered = panel.triggerHeaderActionForTest("New Topic")

        assertTrue(triggered)
        assertTrue(uiService.dispatched.filterIsInstance<AddTopicNodeCommand>().isEmpty())
    }

    @Test
    fun `rename topic keeps status bar unchanged while dispatching rename command`() {
        val uiService = RecordingUiService()
        val prompts = ArrayDeque(listOf("Renamed Guide"))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
        )
        panel.render(sampleState())
        assertTrue(panel.selectTreeNodeForTest("n2"))

        val triggered = panel.triggerTocContextActionForTest("Edit Title")

        assertTrue(triggered)
        assertTrue(uiService.dispatched.any { it is RenameTopicNodeCommand })
        assertEquals("", panel.statusTextForTest())
    }

    @Test
    fun `rename in fallback mode keeps selection on renamed node when id changes`() {
        var state = sampleFallbackState()
        val uiService = object : TopicTreeUiService {
            override fun dispatch(command: TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome> {
                if (command is RenameTopicNodeCommand && command.nodeId == "page:install.md") {
                    state = state.copy(
                        nodes = listOf(
                            com.authord.mkdocs.ports.topic.TopicNavNode(
                                nodeId = "page:index.md",
                                title = "Index",
                                path = "index.md",
                            ),
                            com.authord.mkdocs.ports.topic.TopicNavNode(
                                nodeId = "page:guides.md",
                                title = command.newTitle,
                                path = "guides.md",
                            ),
                        ),
                        navOrderedPaths = listOf("index.md", "guides.md"),
                    )
                }
                return TopicGatewayResult.Success(
                    TopicSyncOutcome(
                        transactionId = command.commandId,
                        applied = true,
                        rolledBack = false,
                        compensated = false,
                        message = "Applied",
                    ),
                )
            }

            override fun refreshActiveTree(): TopicGatewayResult<TopicSyncOutcome> {
                return TopicGatewayResult.Success(
                    TopicSyncOutcome(
                        transactionId = "refresh",
                        applied = true,
                        rolledBack = false,
                        compensated = false,
                        message = "Refreshed",
                    ),
                )
            }

            override fun selectInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
                return TopicGatewayResult.Success(
                    TopicInstanceRef(
                        instanceId = instanceId,
                        configPath = "/tmp/project/mkdocs.yml",
                        docsDirPath = "/tmp/project/docs",
                    ),
                )
            }
        }
        val prompts = ArrayDeque(listOf("Guides"))
        val panel = panelWithDefaults(
            uiService = uiService,
            promptInputProvider = { _, _, _ -> prompts.removeFirstOrNull() },
            reconcileStateProvider = { state },
        )
        panel.render(state)
        assertTrue(panel.selectTreeNodeForTest("page:install.md"))

        val triggered = panel.triggerTocContextActionForTest("Edit Title")

        assertTrue(triggered)
        assertEquals("page:guides.md", panel.selectedTreeNodeIdForTest())
    }

    private fun panelWithDefaults(
        uiService: TopicTreeUiService = RecordingUiService(),
        promptInputProvider: (title: String, message: String, initial: String?) -> String? = { _, _, _ -> null },
        duplicatePathPrompt: (requestedPath: String, suggestedPath: String) -> Boolean = { _, _ -> true },
        reconcileStateProvider: () -> StartupTreeState? = { sampleState() },
    ): TopicTreeWorkspacePanel {
        val registry = RecordingInstanceRegistryPort(
            instances = mutableListOf(
                TopicInstanceRef(
                    instanceId = "default",
                    configPath = "/tmp/project/mkdocs.yml",
                    docsDirPath = "/tmp/project/docs",
                ),
            ),
            activeInstanceId = "default",
        )
        val recovery = TopicTreeFailureRecoveryPresenter()
        val controllers = TopicTreeControllers(
            actionController = TopicTreeActionController(
                uiService = uiService,
                failureRecoveryPresenter = recovery,
                commandIdFactory = { "cmd-fixed" },
                nodeIdFactory = { "node-fixed" },
            ),
            dragDropController = TopicTreeDragDropController(
                uiService = uiService,
                failureRecoveryPresenter = recovery,
                commandIdFactory = { "drag-fixed" },
            ),
            failureRecoveryPresenter = recovery,
            instanceSwitchCoordinator = InstanceSwitchCoordinator(uiService = uiService),
            instanceRegistryPort = registry,
        )

        return TopicTreeWorkspacePanel(
            project = null,
            controllersProvider = { controllers },
            reconcileStateProvider = reconcileStateProvider,
            promptInputProvider = promptInputProvider,
            duplicatePathPrompt = duplicatePathPrompt,
        )
    }

    private fun sampleState(): StartupTreeState {
        return StartupTreeState(
            source = StartupTreeSource.NAV,
            nodes = listOf(
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "n1",
                    title = "Intro",
                    path = "index.md",
                ),
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "n2",
                    title = "Guide",
                    path = "guide/index.md",
                ),
            ),
            navOrderedPaths = listOf("index.md", "guide/index.md"),
            unlinkedPaths = emptyList(),
            validationIssues = emptyList(),
            destructiveChangesApplied = false,
            instanceId = "default",
        )
    }

    private fun sampleFallbackState(): StartupTreeState {
        return StartupTreeState(
            source = StartupTreeSource.FALLBACK,
            nodes = listOf(
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "page:index.md",
                    title = "Index",
                    path = "index.md",
                ),
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "page:install.md",
                    title = "Install",
                    path = "install.md",
                ),
            ),
            navOrderedPaths = listOf("index.md", "install.md"),
            unlinkedPaths = emptyList(),
            validationIssues = emptyList(),
            destructiveChangesApplied = false,
            instanceId = "default",
        )
    }
    @Test
    fun `resolves relative file path for active instance node`() {
        val panel = panelWithDefaults()
        panel.render(sampleState())

        val node = com.authord.mkdocs.ports.topic.TopicNavNode(
            nodeId = "n2",
            title = "Guide",
            path = "guide/index.md",
        )
        // We need to simulate the view object being passed to resolveFilePath
        val view = TopicTreeNodeView(
            nodeId = node.nodeId,
            title = node.title,
            parentNodeId = "root",
            path = node.path,
        )

        val resolved = panel.resolveFilePath(view)

        assertEquals("guide/index.md", resolved)
    }

    @Test
    fun `resolves folder selection to section index markdown`() {
        val panel = panelWithDefaults()
        panel.render(sampleFallbackSectionState())

        val folderView = TopicTreeNodeView(
            nodeId = "section:install",
            title = "Install",
            parentNodeId = "root",
            path = null,
        )

        val resolved = panel.resolveFilePath(folderView)

        assertEquals("install/index.md", resolved)
    }

    @Test
    fun `fallback section index node is hidden from toc children`() {
        val panel = panelWithDefaults()
        panel.render(sampleFallbackSectionState())

        val childNodeIds = panel.childNodeIdsForTest("section:install")

        assertEquals(listOf("page:install/a1.md"), childNodeIds)
    }

    @Test
    fun `nav converted representative page child is hidden from toc children`() {
        val panel = panelWithDefaults()
        panel.render(sampleNavConvertedSectionState())

        val childNodeIds = panel.childNodeIdsForTest("child")

        assertEquals(listOf("grand-child"), childNodeIds)
    }

    @Test
    fun `nav converted representative page child is hidden for deeper levels`() {
        val panel = panelWithDefaults()
        panel.render(sampleNavConvertedSectionState())

        val childNodeIds = panel.childNodeIdsForTest("grand-child")

        assertEquals(listOf("great-grand-child"), childNodeIds)
    }

    @Test
    fun `nav converted section resolves to preserved representative page path`() {
        val panel = panelWithDefaults()
        panel.render(sampleNavConvertedSectionState())

        val sectionView = TopicTreeNodeView(
            nodeId = "child",
            title = "child",
            parentNodeId = "root",
            path = null,
        )

        val resolved = panel.resolveFilePath(sectionView)

        assertEquals("child.md", resolved)
    }

    @Test
    fun `nav converted deep section resolves to preserved representative page path`() {
        val panel = panelWithDefaults()
        panel.render(sampleNavConvertedSectionState())

        val sectionView = TopicTreeNodeView(
            nodeId = "grand-child",
            title = "grand child",
            parentNodeId = "child",
            path = null,
        )

        val resolved = panel.resolveFilePath(sectionView)

        assertEquals("child/grand-child.md", resolved)
    }

    @Test
    fun `toActualChildOrderIndex inserts before first visible child when hidden representative trails list`() {
        val panel = panelWithDefaults()
        panel.render(sampleSectionWithTrailingHiddenIndexState())
        val method = TopicTreeWorkspacePanel::class.java.getDeclaredMethod(
            "toActualChildOrderIndex",
            String::class.java,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val actual = method.invoke(panel, "section:guides", 0) as Int

        assertEquals(0, actual)
    }

    private fun sampleFallbackSectionState(): StartupTreeState {
        return StartupTreeState(
            source = StartupTreeSource.FALLBACK,
            nodes = listOf(
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "section:install",
                    title = "Install",
                    children = listOf(
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "page:install/index.md",
                            title = "Install",
                            path = "install/index.md",
                        ),
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "page:install/a1.md",
                            title = "A1",
                            path = "install/a1.md",
                        ),
                    ),
                ),
            ),
            navOrderedPaths = listOf("install/index.md", "install/a1.md"),
            unlinkedPaths = emptyList(),
            validationIssues = emptyList(),
            destructiveChangesApplied = false,
            instanceId = "default",
        )
    }

    private fun sampleNavConvertedSectionState(): StartupTreeState {
        return StartupTreeState(
            source = StartupTreeSource.NAV,
            nodes = listOf(
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "child",
                    title = "child",
                    children = listOf(
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "n-0-0",
                            title = "child",
                            path = "child.md",
                        ),
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "grand-child",
                            title = "grand child",
                            children = listOf(
                                com.authord.mkdocs.ports.topic.TopicNavNode(
                                    nodeId = "n-0-1-0",
                                    title = "grand child",
                                    path = "child/grand-child.md",
                                ),
                                com.authord.mkdocs.ports.topic.TopicNavNode(
                                    nodeId = "great-grand-child",
                                    title = "great grand child",
                                    path = "child/grand-child/great-grand-child.md",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            navOrderedPaths = listOf(
                "child.md",
                "child/grand-child.md",
                "child/grand-child/great-grand-child.md",
            ),
            unlinkedPaths = emptyList(),
            validationIssues = emptyList(),
            destructiveChangesApplied = false,
            instanceId = "default",
        )
    }

    private fun sampleSectionWithTrailingHiddenIndexState(): StartupTreeState {
        return StartupTreeState(
            source = StartupTreeSource.NAV,
            nodes = listOf(
                com.authord.mkdocs.ports.topic.TopicNavNode(
                    nodeId = "section:guides",
                    title = "Guides",
                    children = listOf(
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "page:guides/a.md",
                            title = "A",
                            path = "guides/a.md",
                        ),
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "page:guides/b.md",
                            title = "B",
                            path = "guides/b.md",
                        ),
                        com.authord.mkdocs.ports.topic.TopicNavNode(
                            nodeId = "page:guides/index.md",
                            title = "Guides",
                            path = "guides/index.md",
                        ),
                    ),
                ),
            ),
            navOrderedPaths = listOf("guides/a.md", "guides/b.md", "guides/index.md"),
            unlinkedPaths = emptyList(),
            validationIssues = emptyList(),
            destructiveChangesApplied = false,
            instanceId = "default",
        )
    }
}

private class RecordingUiService : TopicTreeUiService {
    val dispatched = mutableListOf<TopicTreeCommand>()

    override fun dispatch(command: TopicTreeCommand): TopicGatewayResult<TopicSyncOutcome> {
        dispatched += command
        return TopicGatewayResult.Success(
            TopicSyncOutcome(
                transactionId = command.commandId,
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "Applied",
            ),
        )
    }

    override fun refreshActiveTree(): TopicGatewayResult<TopicSyncOutcome> {
        return TopicGatewayResult.Success(
            TopicSyncOutcome(
                transactionId = "refresh",
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "Refreshed",
            ),
        )
    }

    override fun selectInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        return TopicGatewayResult.Success(
            TopicInstanceRef(
                instanceId = instanceId,
                configPath = "/tmp/project/mkdocs.yml",
                docsDirPath = "/tmp/project/docs",
            ),
        )
    }
}

private class RecordingInstanceRegistryPort(
    private val instances: MutableList<TopicInstanceRef>,
    private var activeInstanceId: String,
) : InstanceRegistryPort {
    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instances.firstOrNull { it.instanceId == "default" })
    }

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> {
        instances.removeAll { it.instanceId == instance.instanceId }
        instances += instance
        return TopicGatewayResult.Success(Unit)
    }

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> {
        return TopicGatewayResult.Success(instances.toList())
    }

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        val selected = instances.firstOrNull { it.instanceId == instanceId }
            ?: return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Unknown instance: $instanceId"),
            )
        activeInstanceId = instanceId
        return TopicGatewayResult.Success(selected)
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instances.firstOrNull { it.instanceId == activeInstanceId })
    }
}
