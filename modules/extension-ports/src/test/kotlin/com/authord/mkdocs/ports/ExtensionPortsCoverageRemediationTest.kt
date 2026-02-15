package com.authord.mkdocs.ports

import com.authord.mkdocs.ports.topic.AddChildTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExistingFileTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddExternalLinkTopicNodeCommand
import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicFileOperation
import com.authord.mkdocs.ports.topic.TopicFileOperationKind
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncEvent
import com.authord.mkdocs.ports.topic.TopicSyncEventType
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommandType
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtensionPortsCoverageRemediationTest {
    @Test
    fun `covers topic sync events and errors`() {
        assertEquals(
            setOf(
                "STARTUP_RECONCILIATION",
                "WATCHER_TRIGGER",
                "MUTATION_APPLY",
                "MUTATION_ROLLBACK",
                "VALIDATION_REPORT",
                "COMPATIBILITY_GATE",
            ),
            TopicSyncEventType.entries.map { it.name }.toSet(),
        )

        val event = TopicSyncEvent(
            eventType = TopicSyncEventType.VALIDATION_REPORT,
            projectId = "project-1",
            instanceId = "instance-1",
            transactionId = "tx-1",
            severity = "WARN",
            message = "validation result",
        )
        assertEquals(TopicSyncEventType.VALIDATION_REPORT, event.eventType)
        assertEquals("project-1", event.projectId)
        assertEquals("instance-1", event.instanceId)
        assertEquals("tx-1", event.transactionId)
        assertEquals("WARN", event.severity)
        assertEquals("validation result", event.message)

        assertEquals(
            setOf(
                "VALIDATION",
                "CONFIG_PARSE",
                "CONFIG_WRITE",
                "FILE_IO",
                "RECONCILIATION",
                "ORCHESTRATION",
                "INSTANCE_SCOPE",
                "UNSUPPORTED",
            ),
            TopicSyncErrorCode.entries.map { it.name }.toSet(),
        )

        val error = DefaultTopicSyncError(
            code = TopicSyncErrorCode.CONFIG_PARSE,
            detail = "invalid yaml",
        )
        assertEquals(TopicSyncErrorCode.CONFIG_PARSE, error.code)
        assertEquals("invalid yaml", error.detail)

        val success: TopicGatewayResult<String> = TopicGatewayResult.Success("ok")
        val failure: TopicGatewayResult<String> = TopicGatewayResult.Failure(error)
        assertTrue(success is TopicGatewayResult.Success)
        assertEquals("ok", success.value)
        assertTrue(failure is TopicGatewayResult.Failure)
        assertEquals(TopicSyncErrorCode.CONFIG_PARSE, failure.error.code)
        assertEquals("invalid yaml", failure.error.detail)
    }

    @Test
    fun `covers topic sync model defaults and explicit values`() {
        val nodeWithDefaults = TopicNavNode(
            nodeId = "n-default",
            title = "Default",
        )
        assertEquals("n-default", nodeWithDefaults.nodeId)
        assertEquals("Default", nodeWithDefaults.title)
        assertEquals(null, nodeWithDefaults.path)
        assertEquals(null, nodeWithDefaults.externalUrl)
        assertTrue(nodeWithDefaults.children.isEmpty())

        val child = TopicNavNode(nodeId = "child", title = "Child", path = "child.md")
        val nodeWithOverrides = TopicNavNode(
            nodeId = "n-custom",
            title = "Custom",
            path = "custom.md",
            externalUrl = "https://example.test",
            children = listOf(child),
        )
        assertEquals("custom.md", nodeWithOverrides.path)
        assertEquals("https://example.test", nodeWithOverrides.externalUrl)
        assertEquals(1, nodeWithOverrides.children.size)
        assertEquals("child", nodeWithOverrides.children.single().nodeId)

        val configWithDefaultNotInNav = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(nodeWithDefaults),
        )
        assertEquals("docs", configWithDefaultNotInNav.docsDir)
        assertEquals(1, configWithDefaultNotInNav.nav.size)
        assertTrue(configWithDefaultNotInNav.notInNav.isEmpty())

        assertEquals(
            setOf("CREATE", "DELETE", "RENAME", "MOVE", "REWRITE_LINKS"),
            TopicFileOperationKind.entries.map { it.name }.toSet(),
        )

        val operationWithDefaultTarget = TopicFileOperation(
            kind = TopicFileOperationKind.CREATE,
            sourcePath = "docs/new.md",
        )
        assertEquals(TopicFileOperationKind.CREATE, operationWithDefaultTarget.kind)
        assertEquals("docs/new.md", operationWithDefaultTarget.sourcePath)
        assertEquals(null, operationWithDefaultTarget.targetPath)

        val operationWithTarget = TopicFileOperation(
            kind = TopicFileOperationKind.MOVE,
            sourcePath = "docs/from.md",
            targetPath = "docs/to.md",
        )
        assertEquals("docs/to.md", operationWithTarget.targetPath)

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = "/repo/mkdocs.yml",
            docsDirPath = "/repo/docs",
        )
        val transaction = TopicSyncTransaction(
            transactionId = "tx-default",
            instance = instance,
            command = ValidateTopicTreeCommand("cmd-1", "tree-1"),
        )
        assertEquals("tx-default", transaction.transactionId)
        assertEquals("default", transaction.instance.instanceId)
        assertTrue(transaction.fileOperations.isEmpty())

        val outcome = TopicSyncOutcome(
            transactionId = "tx-outcome",
            applied = true,
            rolledBack = false,
            compensated = false,
            message = "applied",
        )
        assertEquals("tx-outcome", outcome.transactionId)
        assertTrue(outcome.applied)
        assertEquals(false, outcome.rolledBack)
        assertEquals(false, outcome.compensated)
        assertEquals("applied", outcome.message)
    }

    @Test
    fun `covers additional command dto variants`() {
        val addWithSource = AddTopicNodeCommand(
            commandId = "cmd-add",
            treeId = "tree-1",
            parentNodeId = "root",
            nodeId = "node-1",
            title = "Node 1",
            orderIndex = 0,
            sourcePath = "docs/node-1.md",
        )
        assertEquals("docs/node-1.md", addWithSource.sourcePath)
        assertEquals(TopicTreeCommandType.ADD, addWithSource.commandType)

        val addChild = AddChildTopicNodeCommand(
            commandId = "cmd-child",
            treeId = "tree-1",
            targetNodeId = "target-1",
            childNodeId = "child-1",
            childTitle = "Child 1",
            childOrderIndex = 1,
            childSourcePath = "docs/child-1.md",
        )
        assertEquals("target-1", addChild.targetNodeId)
        assertEquals("child-1", addChild.childNodeId)
        assertEquals("Child 1", addChild.childTitle)
        assertEquals(1, addChild.childOrderIndex)
        assertEquals("docs/child-1.md", addChild.childSourcePath)
        assertEquals(TopicTreeCommandType.ADD_CHILD, addChild.commandType)

        val addChildWithDefaultSource = AddChildTopicNodeCommand(
            commandId = "cmd-child-default",
            treeId = "tree-1",
            targetNodeId = "target-2",
            childNodeId = "child-2",
            childTitle = "Child 2",
            childOrderIndex = 2,
        )
        assertEquals(null, addChildWithDefaultSource.childSourcePath)
        assertEquals(TopicTreeCommandType.ADD_CHILD, addChildWithDefaultSource.commandType)

        val addExisting = AddExistingFileTopicNodeCommand(
            commandId = "cmd-existing",
            treeId = "tree-1",
            parentNodeId = "root",
            nodeId = "node-existing",
            title = "Existing",
            relativePath = "docs/existing.md",
            orderIndex = 2,
        )
        assertEquals("root", addExisting.parentNodeId)
        assertEquals("node-existing", addExisting.nodeId)
        assertEquals("Existing", addExisting.title)
        assertEquals("docs/existing.md", addExisting.relativePath)
        assertEquals(2, addExisting.orderIndex)
        assertEquals(TopicTreeCommandType.ADD_EXISTING_FILE, addExisting.commandType)

        val addExternal = AddExternalLinkTopicNodeCommand(
            commandId = "cmd-external",
            treeId = "tree-1",
            parentNodeId = "root",
            nodeId = "node-external",
            title = "External",
            externalUrl = "https://example.test/docs",
            orderIndex = 3,
        )
        assertEquals("root", addExternal.parentNodeId)
        assertEquals("node-external", addExternal.nodeId)
        assertEquals("External", addExternal.title)
        assertEquals("https://example.test/docs", addExternal.externalUrl)
        assertEquals(3, addExternal.orderIndex)
        assertEquals(TopicTreeCommandType.ADD_EXTERNAL_LINK, addExternal.commandType)
    }

    @Test
    fun `covers topic tree api version registry defaults`() {
        assertEquals("2.0.0", TOPIC_TREE_PORT_API_VERSION)
        assertEquals("2.0.0", TOPIC_TREE_APP_SERVICE_API_VERSION)
        assertEquals("2.0.0", TOPIC_TREE_UI_SERVICE_API_VERSION)

        assertEquals("2.0.0", TOPIC_TREE_API_VERSION_REGISTRY.port)
        assertEquals("2.0.0", TOPIC_TREE_API_VERSION_REGISTRY.applicationService)
        assertEquals("2.0.0", TOPIC_TREE_API_VERSION_REGISTRY.uiService)

        val customRegistry = TopicTreeApiVersionRegistry(
            port = "3.0.0",
            applicationService = "3.1.0",
            uiService = "3.2.0",
        )
        assertEquals("3.0.0", customRegistry.port)
        assertEquals("3.1.0", customRegistry.applicationService)
        assertEquals("3.2.0", customRegistry.uiService)
    }
}
