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
        assertEquals("# Quick Start\n", docsGateway.createdContentByPath["quick-start.md"])
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
        assertEquals("# Install Guide\n", docsGateway.createdContentByPath["guides/install-guide.md"])
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
    fun `no-nav rename section alias rewrites folder markdown paths without config writes and without heading sync`() {
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
        assertTrue(docsGateway.headingUpdates.isEmpty())
        assertTrue(configGateway.writes.isEmpty())
    }

    @Test
    fun `rename section in nav mode updates nav title without renaming file or syncing heading`() {
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
                                title = "Guides",
                                path = "guides/index.md",
                            ),
                            TopicNavNode(
                                nodeId = "guides-install",
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
                    transactionId = "tx-rename-nav-section",
                    instance = instance,
                    command = RenameTopicNodeCommand(
                        commandId = "cmd-rename-nav-section",
                        treeId = "default",
                        nodeId = "guides",
                        newTitle = "How To",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertTrue(docsGateway.calls.isEmpty())
        assertTrue(docsGateway.headingUpdates.isEmpty())
        assertEquals("How To", configGateway.writes.last().nav.single().title)
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
    fun `same-parent move applies pre-removal index semantics in config nav`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(nodeId = "a", title = "A", path = "a.md"),
                    TopicNavNode(nodeId = "b", title = "B", path = "b.md"),
                    TopicNavNode(nodeId = "c", title = "C", path = "c.md"),
                ),
            ),
        )
        val docsGateway = RecordingDocsGatewayForDerivation()
        val orchestrator = orchestrator(configGateway, docsGateway)

        val outcome = requireSuccess(
            orchestrator.apply(
                TopicSyncTransaction(
                    transactionId = "tx-same-parent-move",
                    instance = instance,
                    command = MoveTopicNodeCommand(
                        commandId = "cmd-same-parent-move",
                        treeId = "default",
                        nodeId = "a",
                        newParentNodeId = "root",
                        newOrderIndex = 2,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertTrue(docsGateway.calls.isEmpty())
        assertEquals(listOf("b", "a", "c"), configGateway.writes.last().nav.map { it.nodeId })
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
    fun `rename topic updates title without renaming markdown path`() {
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
        assertTrue(docsGateway.calls.isEmpty())
        assertTrue(docsGateway.headingUpdates.isEmpty())
        val written = configGateway.writes.last()
        assertEquals("guide/old.md", written.nav.single().path)
        assertEquals("New Name", written.nav.single().title)
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

    @Test
    fun `remove nested branch deletes all descendant markdown files`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guides",
                        title = "Guides",
                        children = listOf(
                            TopicNavNode(
                                nodeId = "guides__home",
                                title = "Guides Home",
                                path = "guides/index.md",
                            ),
                            TopicNavNode(
                                nodeId = "install",
                                title = "Install",
                                children = listOf(
                                    TopicNavNode(
                                        nodeId = "install__home",
                                        title = "Install Home",
                                        path = "guides/install/index.md",
                                    ),
                                    TopicNavNode(
                                        nodeId = "install-linux",
                                        title = "Linux",
                                        path = "guides/install/linux.md",
                                    ),
                                ),
                            ),
                            TopicNavNode(
                                nodeId = "faq",
                                title = "FAQ",
                                path = "guides/faq.md",
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
                    transactionId = "tx-remove-nested-branch",
                    instance = instance,
                    command = RemoveTopicNodeCommand(
                        commandId = "cmd-remove-nested-branch",
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
                "delete:guides/install/index.md:RECOVERABLE",
                "delete:guides/install/linux.md:RECOVERABLE",
                "delete:guides/faq.md:RECOVERABLE",
            ),
            docsGateway.calls.toSet(),
        )
        assertTrue(configGateway.writes.last().nav.isEmpty())
    }

    @Test
    fun `remove child collapses empty parent section in nav mode`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "guide",
                        title = "Guide",
                        children = listOf(
                            TopicNavNode(
                                nodeId = "guide-intro",
                                title = "Intro",
                                path = "guide/intro.md",
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
                    transactionId = "tx-remove-leaf",
                    instance = instance,
                    command = RemoveTopicNodeCommand(
                        commandId = "cmd-remove-leaf",
                        treeId = "default",
                        nodeId = "guide-intro",
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(listOf("delete:guide/intro.md:RECOVERABLE"), docsGateway.calls)
        assertTrue(configGateway.writes.last().nav.isEmpty())
    }

    @Test
    fun `add child to node with both path and children preserves existing children and page path`() {
        val configGateway = MutableConfigGatewayForDerivation(
            MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(
                    TopicNavNode(
                        nodeId = "mixed",
                        title = "Mixed",
                        path = "mixed/index.md",
                        children = listOf(
                            TopicNavNode(
                                nodeId = "mixed-existing",
                                title = "Existing",
                                path = "mixed/existing.md",
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
                    transactionId = "tx-add-mixed-child",
                    instance = instance,
                    command = AddChildTopicNodeCommand(
                        commandId = "cmd-add-mixed-child",
                        treeId = "default",
                        targetNodeId = "mixed",
                        childNodeId = "mixed-new",
                        childTitle = "New Child",
                        childOrderIndex = 1,
                        childSourcePath = null,
                    ),
                ),
            ),
        )

        assertTrue(outcome.applied)
        assertEquals(listOf("create:mixed/new-child.md"), docsGateway.calls)
        val mixed = configGateway.writes.last().nav.single()
        assertEquals("mixed/index.md", mixed.path)
        assertEquals(listOf("mixed-existing", "mixed-new"), mixed.children.map { it.nodeId })
    }

    @Test
    fun `move section in nav mode rewrites descendant markdown paths`() {
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
                        ),
                    ),
                    TopicNavNode(
                        nodeId = "install",
                        title = "Install",
                        children = listOf(
                            TopicNavNode(
                                nodeId = "install__page",
                                title = "Install Home",
                                path = "install/index.md",
                            ),
                            TopicNavNode(
                                nodeId = "install-a1",
                                title = "A1",
                                path = "install/a1.md",
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
                    transactionId = "tx-move-section-nav",
                    instance = instance,
                    command = MoveTopicNodeCommand(
                        commandId = "cmd-move-section-nav",
                        treeId = "default",
                        nodeId = "install",
                        newParentNodeId = "guides",
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
        val guides = configGateway.writes.last().nav.first { it.nodeId == "guides" }
        val moved = guides.children.first { it.nodeId == "install" }
        assertEquals(listOf("guides/install/index.md", "guides/install/a1.md"), moved.children.map { it.path })
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
    val createdContentByPath = mutableMapOf<String, String>()
    val headingUpdates = mutableListOf<String>()

    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> {
        calls += "create:$relativePath"
        createdContentByPath[relativePath] = initialContent
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

    override fun upsertMarkdownTitleHeading(
        instance: TopicInstanceRef,
        relativePath: String,
        title: String,
    ): TopicGatewayResult<String> {
        headingUpdates += "heading:$relativePath=$title"
        return TopicGatewayResult.Success(relativePath)
    }
}
