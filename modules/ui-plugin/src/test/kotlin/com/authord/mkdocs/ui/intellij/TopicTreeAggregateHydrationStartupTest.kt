package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.TopicTreeAggregate
import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TopicTreeAggregateHydrationStartupTest {
    @Test
    fun `startup reconciliation hydrates aggregate from loaded config nav`() {
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/tmp/project/mkdocs.yml",
            docsDirPath = "/tmp/project/docs",
        )
        val startupConfig = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(
                    nodeId = "guide-node",
                    title = "Guide",
                    path = "guide.md",
                ),
            ),
        )
        val topicTreePort = TopicTreeMutationService()
        val uiService = uiService(topicTreePort, instance)
        val project = IntellijTestFixtures.project(basePath = "/tmp/project", locationHash = "startup-hydration")
        val fixture = IntellijTestFixtures.toolWindowFixture()
        val runtimeService = PluginRuntimeIntegrationService(project)

        val factory = MkdocsToolWindowFactory(
            runtimeServiceResolver = { runtimeService },
            previewContentFactory = { HydrationPreviewContent() },
            topicTreeUiServiceResolver = { uiService },
            defaultInstanceResolver = { TopicGatewayResult.Success(instance) },
            configLoader = { TopicGatewayResult.Success(startupConfig) },
            mkdocsConfigPresenceResolver = { true },
        )

        factory.createToolWindowContent(project, fixture.toolWindow)

        val nodeIds = aggregateSnapshot(topicTreePort, instance.instanceId).map { it.nodeId }.toSet()
        assertTrue(nodeIds.contains("guide-node"), "Expected startup hydration to seed 'guide-node' but got: $nodeIds")
    }

    @Test
    fun `startup reconciliation hydrates aggregate from fallback nodes when nav is absent`() {
        val projectRoot = createTempDirectory(prefix = "startup-fallback-hydration-")
        try {
            val docsDir = projectRoot.resolve("docs")
            Files.createDirectories(docsDir)
            Files.writeString(docsDir.resolve("index.md"), "# Home\n")
            val instance = TopicInstanceRef(
                instanceId = "default",
                configPath = projectRoot.resolve("mkdocs.yml").toString(),
                docsDirPath = docsDir.toString(),
            )
            val startupConfig = MkDocsConfigDocument(
                docsDir = "docs",
                nav = emptyList(),
                navPresent = false,
            )
            val topicTreePort = TopicTreeMutationService()
            val uiService = uiService(topicTreePort, instance)
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "startup-fallback-hydration")
            val fixture = IntellijTestFixtures.toolWindowFixture()
            val runtimeService = PluginRuntimeIntegrationService(project)

            val factory = MkdocsToolWindowFactory(
                runtimeServiceResolver = { runtimeService },
                previewContentFactory = { HydrationPreviewContent() },
                topicTreeUiServiceResolver = { uiService },
                defaultInstanceResolver = { TopicGatewayResult.Success(instance) },
                configLoader = { TopicGatewayResult.Success(startupConfig) },
                mkdocsConfigPresenceResolver = { true },
            )

            factory.createToolWindowContent(project, fixture.toolWindow)

            val nodeIds = aggregateSnapshot(topicTreePort, instance.instanceId).map { it.nodeId }.toSet()
            assertTrue(nodeIds.contains("page:index.md"), "Expected fallback hydration to seed page:index.md but got: $nodeIds")

            val renameOutcome = requireSuccess(
                uiService.dispatch(
                    RenameTopicNodeCommand(
                        commandId = "cmd-rename-fallback",
                        treeId = instance.instanceId,
                        nodeId = "page:index.md",
                        newTitle = "Home Updated",
                    ),
                ),
            )
            assertTrue(renameOutcome.applied)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `orchestrator hydrates aggregate before first rename command`() {
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/tmp/project/mkdocs.yml",
            docsDirPath = "/tmp/project/docs",
        )
        val configGateway = HydrationConfigGateway(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guide-node",
                        title = "Guide",
                        path = "guide.md",
                    ),
                ),
            ),
        )
        val docsGateway = HydrationRecordingDocsGateway()
        val topicTreePort = TopicTreeMutationService()
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = topicTreePort,
            mkDocsConfigGateway = configGateway,
            docsFileGateway = docsGateway,
        )

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-rename-hydrated",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-rename-hydrated",
                        treeId = instance.instanceId,
                        nodeId = "guide-node",
                        newTitle = "Guide Updated",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "rename:guide.md->guide-updated.md",
                "rewrite:guide.md->guide-updated.md",
            ),
            docsGateway.calls,
        )
        val persistedPath = configGateway.current.nav.single().path
        assertEquals("guide-updated.md", persistedPath)
    }

    @Test
    fun `orchestrator synthesizes fallback nav from docs when config nav is missing for rename`() {
        val projectRoot = createTempDirectory(prefix = "orchestrator-no-nav-rename-")
        try {
            val docsDir = projectRoot.resolve("docs")
            Files.createDirectories(docsDir)
            Files.writeString(docsDir.resolve("index.md"), "# Home\n")
            val instance = TopicInstanceRef(
                instanceId = "default",
                configPath = projectRoot.resolve("mkdocs.yml").toString(),
                docsDirPath = docsDir.toString(),
            )
            val configGateway = HydrationConfigGateway(
                MkDocsConfigDocument(
                    docsDir = "docs",
                    nav = emptyList(),
                    navPresent = false,
                ),
            )
            val docsGateway = HydrationRecordingDocsGateway()
            val topicTreePort = TopicTreeMutationService()
            val orchestrator = TopicTreeSyncOrchestratorService(
                topicTreePort = topicTreePort,
                mkDocsConfigGateway = configGateway,
                docsFileGateway = docsGateway,
            )

            val outcome = requireSuccess(
                orchestrator.apply(
                    TopicSyncTransaction(
                        transactionId = "tx-no-nav-rename",
                        instance = instance,
                        command = RenameTopicNodeCommand(
                            commandId = "cmd-no-nav-rename",
                            treeId = instance.instanceId,
                            nodeId = "page:index.md",
                            newTitle = "Home Updated",
                        ),
                    ),
                ),
            )

            assertTrue(outcome.applied)
            assertEquals(
                listOf(
                    "rename:index.md->home-updated.md",
                    "rewrite:index.md->home-updated.md",
                ),
                docsGateway.calls,
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `orchestrator synthesizes fallback nav from docs when config nav is missing for add child`() {
        val projectRoot = createTempDirectory(prefix = "orchestrator-no-nav-add-child-")
        try {
            val docsDir = projectRoot.resolve("docs")
            Files.createDirectories(docsDir)
            Files.writeString(docsDir.resolve("index.md"), "# Home\n")
            val instance = TopicInstanceRef(
                instanceId = "default",
                configPath = projectRoot.resolve("mkdocs.yml").toString(),
                docsDirPath = docsDir.toString(),
            )
            val configGateway = HydrationConfigGateway(
                MkDocsConfigDocument(
                    docsDir = "docs",
                    nav = emptyList(),
                    navPresent = false,
                ),
            )
            val docsGateway = HydrationRecordingDocsGateway()
            val topicTreePort = TopicTreeMutationService()
            val orchestrator = TopicTreeSyncOrchestratorService(
                topicTreePort = topicTreePort,
                mkDocsConfigGateway = configGateway,
                docsFileGateway = docsGateway,
            )

            val outcome = requireSuccess(
                orchestrator.apply(
                    TopicSyncTransaction(
                        transactionId = "tx-no-nav-add-child",
                        instance = instance,
                        command = AddChildTopicNodeCommand(
                            commandId = "cmd-no-nav-add-child",
                            treeId = instance.instanceId,
                            targetNodeId = "page:index.md",
                            childNodeId = "install",
                            childTitle = "Install",
                            childOrderIndex = 1,
                            childSourcePath = null,
                        ),
                    ),
                ),
            )

            assertTrue(outcome.applied)
            assertEquals(listOf("create:install.md"), docsGateway.calls)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `no-nav mode refreshes aggregate from docs snapshot between transactions`() {
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/tmp/project/mkdocs.yml",
            docsDirPath = "/tmp/project/docs",
        )
        val configGateway = HydrationConfigGateway(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = emptyList(),
                navPresent = false,
            ),
        )
        val docsGateway = HydrationRecordingDocsGateway()
        val snapshots = listOf(
            listOf("/tmp/project/docs/index.md", "/tmp/project/docs/install.md"),
            listOf("/tmp/project/docs/index.md", "/tmp/project/docs/guide.md"),
        )
        var snapshotCursor = 0
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = TopicTreeMutationService(),
            mkDocsConfigGateway = configGateway,
            docsFileGateway = docsGateway,
            docsMarkdownPathCollector = {
                val index = snapshotCursor.coerceAtMost(snapshots.lastIndex)
                snapshotCursor += 1
                snapshots[index]
            },
        )

        requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-refresh-prime",
                    instance = instance,
                    command = ValidateTopicTreeCommand(
                        commandId = "cmd-no-nav-refresh-prime",
                        treeId = instance.instanceId,
                    ),
                ),
            ),
        )

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-refresh-rename",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-no-nav-refresh-rename",
                        treeId = instance.instanceId,
                        nodeId = "page:guide.md",
                        newTitle = "Guide Updated",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "rename:guide.md->guide-updated.md",
                "rewrite:guide.md->guide-updated.md",
            ),
            docsGateway.calls,
        )
    }

    @Test
    fun `reload hydration refreshes cached config state before follow-up mutations`() {
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/tmp/project/mkdocs.yml",
            docsDirPath = "/tmp/project/docs",
        )
        val staleConfig = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(
                    nodeId = "stale-node",
                    title = "Stale",
                    path = "stale.md",
                ),
            ),
        )
        val refreshedConfig = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(
                    nodeId = "fresh-node",
                    title = "Fresh",
                    path = "fresh.md",
                ),
            ),
        )
        val configGateway = HydrationConfigGateway(staleConfig)
        val docsGateway = HydrationRecordingDocsGateway()
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = HydrationAlwaysSuccessTopicTreePort(),
            mkDocsConfigGateway = configGateway,
            docsFileGateway = docsGateway,
        )

        requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-prime-cache",
                    instance = instance,
                    command = ValidateTopicTreeCommand(
                        commandId = "cmd-prime-cache",
                        treeId = instance.instanceId,
                    ),
                ),
            ),
        )

        orchestrator.hydrateAggregateFromConfig(
            treeId = instance.instanceId,
            instanceId = instance.instanceId,
            config = refreshedConfig,
        )

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-rename-fresh",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-rename-fresh",
                        treeId = instance.instanceId,
                        nodeId = "fresh-node",
                        newTitle = "Fresh Updated",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "rename:fresh.md->fresh-updated.md",
                "rewrite:fresh.md->fresh-updated.md",
            ),
            docsGateway.calls,
        )
    }

    private fun uiService(
        topicTreePort: TopicTreeMutationService,
        instance: TopicInstanceRef,
    ): TopicTreeUiServiceImpl {
        val orchestrator = TopicTreeSyncOrchestratorService(
            topicTreePort = topicTreePort,
            mkDocsConfigGateway = HydrationConfigGateway(MkDocsConfigDocument(docsDir = "docs", nav = emptyList())),
            docsFileGateway = HydrationNoOpDocsGateway(),
        )
        val registry = HydrationSingleInstanceRegistry(instance)
        return TopicTreeUiServiceImpl(
            applicationService = TopicTreeApplicationServiceImpl(orchestrator, registry),
            instanceRegistryPort = registry,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun aggregateSnapshot(port: TopicTreeMutationService, treeId: String): List<com.authord.mkdocs.core.topic.TopicNode> {
        val field = TopicTreeMutationService::class.java.getDeclaredField("aggregateByTreeId").apply { isAccessible = true }
        val map = field.get(port) as MutableMap<String, TopicTreeAggregate>
        val aggregate = map[treeId] ?: return emptyList()
        return aggregate.snapshot()
    }

    private fun requireSuccess(result: TopicGatewayResult<TopicSyncOutcome>): TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class HydrationPreviewContent : PreviewContent {
    override val component: JComponent = JPanel()

    override fun loadUrl(url: String) = Unit
}

private class HydrationConfigGateway(initial: MkDocsConfigDocument) : MkDocsConfigGateway {
    var current: MkDocsConfigDocument = initial
        private set

    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Success(current)
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        current = document
        return TopicGatewayResult.Success(Unit)
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Success("docs_dir: docs\nnav: []\n")
    }
}

private class HydrationRecordingDocsGateway : DocsFileGateway {
    val calls = mutableListOf<String>()

    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> {
        calls += "create:$relativePath"
        return TopicGatewayResult.Success(relativePath)
    }

    override fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String> {
        calls += "delete:$relativePath:$mode"
        return TopicGatewayResult.Success(relativePath)
    }

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        calls += "rename:$fromRelativePath->$toRelativePath"
        return TopicGatewayResult.Success(toRelativePath)
    }

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        calls += "move:$fromRelativePath->$toRelativePath"
        return TopicGatewayResult.Success(toRelativePath)
    }

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        calls += "rewrite:$fromRelativePath->$toRelativePath"
        return TopicGatewayResult.Success(0)
    }
}

private class HydrationNoOpDocsGateway : DocsFileGateway {
    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> = TopicGatewayResult.Success(relativePath)

    override fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String> = TopicGatewayResult.Success(relativePath)

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> = TopicGatewayResult.Success(toRelativePath)

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> = TopicGatewayResult.Success(toRelativePath)

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> = TopicGatewayResult.Success(0)
}

private class HydrationSingleInstanceRegistry(
    private val instance: TopicInstanceRef,
) : InstanceRegistryPort {
    override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instance)
    }

    override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> = TopicGatewayResult.Success(Unit)

    override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> {
        return TopicGatewayResult.Success(listOf(instance))
    }

    override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
        if (instanceId != instance.instanceId) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Unknown instance: $instanceId"),
            )
        }
        return TopicGatewayResult.Success(instance)
    }

    override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> {
        return TopicGatewayResult.Success(instance)
    }
}

private class HydrationAlwaysSuccessTopicTreePort : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            message = "ok",
        )
    }
}
