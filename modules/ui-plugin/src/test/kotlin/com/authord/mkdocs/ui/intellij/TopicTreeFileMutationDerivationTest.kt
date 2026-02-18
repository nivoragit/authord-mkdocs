package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TopicTreeFileMutationDerivationTest {
    private val instance = TopicInstanceRef("default", "/project/mkdocs.yml", "/project/docs")

    @Test
    fun `add topic without source path derives markdown path and creates file`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(docsDir = "docs", nav = emptyList()),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-add",
                    instance = instance,
                    command = AddTopicNodeCommand(
                        commandId = "cmd-add",
                        treeId = "default",
                        parentNodeId = "root",
                        nodeId = "node-add",
                        title = "Quick Start",
                        orderIndex = 0,
                        sourcePath = null,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(listOf("create:quick-start.md"), docsGateway.calls)
        val written = configGateway.writes.last()
        assertEquals("quick-start.md", written.nav.single().path)
        assertTrue(written.navPresent)
    }

    @Test
    fun `add child derives create path when parent is section without direct path`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guides",
                        title = "Guides",
                        path = null,
                        children = listOf(
                            TopicNavNode(
                                nodeId = "guides__page",
                                title = "Guides Home",
                                path = "guides/index.md",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-child",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-child",
                        treeId = "default",
                        targetNodeId = "guides",
                        childNodeId = "install",
                        childTitle = "Install Guide",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(listOf("create:guides/install-guide.md"), docsGateway.calls)
        val written = configGateway.writes.last()
        val guideNode = written.nav.single { it.nodeId == "guides" }
        assertTrue(guideNode.children.any { it.nodeId == "install" && it.path == "guides/install-guide.md" })
    }

    @Test
    fun `no-nav child then child-of-child creates folder hierarchy without config writes`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "page:index.md",
                        title = "Home",
                        path = "index.md",
                    ),
                ),
                navPresent = false,
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val firstOutcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-child-1",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-no-nav-child-1",
                        treeId = "default",
                        targetNodeId = "page:index.md",
                        childNodeId = "install",
                        childTitle = "Install",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )
        assertTrue(firstOutcome.applied)

        val secondOutcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-child-2",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-no-nav-child-2",
                        treeId = "default",
                        targetNodeId = "install",
                        childNodeId = "advanced",
                        childTitle = "Advanced",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )
        assertTrue(secondOutcome.applied)

        assertEquals(
            listOf(
                "create:install.md",
                "move:install.md->install/index.md",
                "rewrite:install.md->install/index.md",
                "create:install/advanced.md",
            ),
            docsGateway.calls,
        )
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `no-nav child-of-child after fallback-style rehydrate preserves folder hierarchy without config writes`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "page:index.md",
                        title = "Home",
                        path = "index.md",
                    ),
                ),
                navPresent = false,
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-rehydrate-1",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-no-nav-rehydrate-1",
                        treeId = "default",
                        targetNodeId = "page:index.md",
                        childNodeId = "install-local-id",
                        childTitle = "Install",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )

        orchestrator.hydrateAggregateFromConfig(
            treeId = "default",
            instanceId = "default",
            config = MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "page:index.md",
                        title = "Index",
                        path = "index.md",
                    ),
                    TopicNavNode(
                        nodeId = "page:install.md",
                        title = "Install",
                        path = "install.md",
                    ),
                ),
                navPresent = false,
            ),
        )

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-rehydrate-2",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-no-nav-rehydrate-2",
                        treeId = "default",
                        targetNodeId = "page:install.md",
                        childNodeId = "advanced",
                        childTitle = "Advanced",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "create:install.md",
                "move:install.md->install/index.md",
                "rewrite:install.md->install/index.md",
                "create:install/advanced.md",
            ),
            docsGateway.calls,
        )
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `no-nav rename section alias rewrites folder markdown paths without config writes`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "install-local",
                        title = "Install",
                        children = listOf(
                            TopicNavNode(nodeId = "install-page", title = "Install", path = "install/index.md"),
                            TopicNavNode(nodeId = "install-a1", title = "A1", path = "install/a1.md"),
                        ),
                    ),
                ),
                navPresent = false,
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-rename-section",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-no-nav-rename-section",
                        treeId = "default",
                        nodeId = "section:install",
                        newTitle = "Guides",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            setOf(
                "rename:install/index.md->guides/index.md",
                "rewrite:install/index.md->guides/index.md",
                "rename:install/a1.md->guides/a1.md",
                "rewrite:install/a1.md->guides/a1.md",
            ),
            docsGateway.calls.toSet(),
        )
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `no-nav move section alias rewrites folder markdown paths without config writes`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guides-page-local",
                        title = "Guides",
                        path = "guides/index.md",
                    ),
                    TopicNavNode(
                        nodeId = "install-local",
                        title = "Install",
                        children = listOf(
                            TopicNavNode(nodeId = "install-page", title = "Install", path = "install/index.md"),
                            TopicNavNode(nodeId = "install-a1", title = "A1", path = "install/a1.md"),
                        ),
                    ),
                ),
                navPresent = false,
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-move-section",
                    instance = instance,
                    command = MoveTopicNodeCommand(
                        commandId = "cmd-no-nav-move-section",
                        treeId = "default",
                        nodeId = "section:install",
                        newParentNodeId = "page:guides/index.md",
                        newOrderIndex = 1,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            setOf(
                "move:install/index.md->guides/install/index.md",
                "rewrite:install/index.md->guides/install/index.md",
                "move:install/a1.md->guides/install/a1.md",
                "rewrite:install/a1.md->guides/install/a1.md",
            ),
            docsGateway.calls.toSet(),
        )
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `no-nav remove section alias deletes subtree markdown files without config writes`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "install-local",
                        title = "Install",
                        children = listOf(
                            TopicNavNode(nodeId = "install-page", title = "Install", path = "install/index.md"),
                            TopicNavNode(nodeId = "install-a1", title = "A1", path = "install/a1.md"),
                        ),
                    ),
                ),
                navPresent = false,
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-no-nav-remove-section",
                    instance = instance,
                    command = RemoveTopicNodeCommand(
                        commandId = "cmd-no-nav-remove-section",
                        treeId = "default",
                        nodeId = "section:install",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            setOf(
                "delete:install/index.md:RECOVERABLE",
                "delete:install/a1.md:RECOVERABLE",
            ),
            docsGateway.calls.toSet(),
        )
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `rename topic updates nav path and performs rename plus link rewrite`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guide-old",
                        title = "Guide Old",
                        path = "guide/old.md",
                    ),
                ),
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-rename",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-rename",
                        treeId = "default",
                        nodeId = "guide-old",
                        newTitle = "New Name",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "rename:guide/old.md->guide/new-name.md",
                "rewrite:guide/old.md->guide/new-name.md",
            ),
            docsGateway.calls,
        )
        val written = configGateway.writes.last()
        assertEquals("guide/new-name.md", written.nav.single().path)
    }

    @Test
    fun `move topic updates nav path and performs move plus link rewrite`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "target",
                        title = "Tutorials",
                        path = null,
                        children = listOf(
                            TopicNavNode(
                                nodeId = "target__page",
                                title = "Tutorials Home",
                                path = "tutorials/index.md",
                            ),
                        ),
                    ),
                    TopicNavNode(
                        nodeId = "moving",
                        title = "Intro",
                        path = "guide/intro.md",
                    ),
                ),
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-move",
                    instance = instance,
                    command = MoveTopicNodeCommand(
                        commandId = "cmd-move",
                        treeId = "default",
                        nodeId = "moving",
                        newParentNodeId = "target",
                        newOrderIndex = 1,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            listOf(
                "move:guide/intro.md->tutorials/intro.md",
                "rewrite:guide/intro.md->tutorials/intro.md",
            ),
            docsGateway.calls,
        )
        val written = configGateway.writes.last()
        val target = written.nav.first { it.nodeId == "target" }
        assertTrue(target.children.any { it.nodeId == "moving" && it.path == "tutorials/intro.md" })
    }

    @Test
    fun `remove topic deletes markdown files for removed subtree`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guides",
                        title = "Guides",
                        children = listOf(
                            TopicNavNode(
                                nodeId = "guides__page",
                                title = "Guides Home",
                                path = "guides/index.md",
                            ),
                            TopicNavNode(
                                nodeId = "install",
                                title = "Install",
                                path = "guides/install.md",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-remove",
                    instance = instance,
                    command = RemoveTopicNodeCommand(
                        commandId = "cmd-remove",
                        treeId = "default",
                        nodeId = "guides",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(
            setOf(
                "delete:guides/index.md:RECOVERABLE",
                "delete:guides/install.md:RECOVERABLE",
            ),
            docsGateway.calls.toSet(),
        )
        val written = configGateway.writes.last()
        assertTrue(written.nav.isEmpty())
    }

    private fun orchestrator(
        configGateway: MutableConfigGatewayForDerivation,
        docsGateway: RecordingDocsGatewayForDerivation,
    ): TopicTreeSyncOrchestratorService {
        return TopicTreeSyncOrchestratorService(
            topicTreePort = SuccessfulTopicTreePortForDerivation(),
            mkDocsConfigGateway = configGateway,
            docsFileGateway = docsGateway,
        )
    }

    private fun requireSuccess(result: TopicGatewayResult<TopicSyncOutcome>): TopicSyncOutcome {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}

private class SuccessfulTopicTreePortForDerivation : TopicTreePort {
    override fun execute(command: TopicTreeCommand): TopicTreeCommandResult {
        return TopicTreeCommandResult(
            commandId = command.commandId,
            status = TopicTreeCommandStatus.SUCCESS,
            treeVersion = 1,
            message = "Applied",
        )
    }
}

private class MutableConfigGatewayForDerivation(
    initial: MkDocsConfigDocument,
) : MkDocsConfigGateway {
    private var current = initial
    val writes = mutableListOf<MkDocsConfigDocument>()

    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        return TopicGatewayResult.Success(current)
    }

    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        current = document
        writes += document
        return TopicGatewayResult.Success(Unit)
    }

    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return TopicGatewayResult.Success("docs_dir: docs\nnav: []\n")
    }
}

private class RecordingDocsGatewayForDerivation : DocsFileGateway {
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
        return TopicGatewayResult.Success(1)
    }
}
