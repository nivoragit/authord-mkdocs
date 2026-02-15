package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
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
            "Reload Configuration",
            "New",
            "Delete",
            "Rename",
            "Root",
            "Child",
            "Collapse All",
        ).forEach { expected ->
            assertTrue(tooltips.contains(expected), "Missing tooltip '$expected'")
        }
    }

    @Test
    fun `menu structure matches contract order`() {
        val panel = panelWithDefaults()

        assertEquals(
            listOf("New Instance", "Rename", "Delete"),
            panel.instancesOverflowMenuLabelsForTest(),
        )
        assertEquals(
            listOf("New Topic", "New Child Topic", "Edit Title", "Remove TOC Element", "Set as Home Page"),
            panel.tocContextMenuLabelsForTest(),
        )
    }

    @Test
    fun `enablement follows capability and selection rules`() {
        val panel = panelWithDefaults()
        panel.render(sampleState())

        val instanceStates = panel.instanceActionStatesForTest()
        assertFalse(instanceStates.getValue("Rename"))
        assertFalse(instanceStates.getValue("Delete"))
        assertEquals(
            listOf(
                "New Instance" to true,
                "Rename" to false,
                "Delete" to false,
            ),
            panel.instancesOverflowMenuStatesForTest(),
        )

        val tocWithoutSelection = panel.tocRowActionStatesForTest()
        assertFalse(tocWithoutSelection.getValue("Child"))
        assertFalse(tocWithoutSelection.getValue("Delete"))

        assertTrue(panel.selectTreeNodeForTest("n1"))
        val tocWithSelection = panel.tocRowActionStatesForTest()
        assertTrue(tocWithSelection.getValue("Child"))
        assertTrue(tocWithSelection.getValue("Delete"))

        val headerVisibility = panel.headerActionVisibilityForTest()
        assertTrue(headerVisibility.getValue("Reload Configuration"))
        assertTrue(headerVisibility.getValue("New"))
        assertTrue(headerVisibility.getValue("Collapse All"))
        assertTrue(headerVisibility.getValue("Root"))
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

    private fun panelWithDefaults(uiService: RecordingUiService = RecordingUiService()): TopicTreeWorkspacePanel {
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
            controllersProvider = { controllers },
            reconcileStateProvider = { sampleState() },
            promptInputProvider = { _, _, _ -> null },
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
