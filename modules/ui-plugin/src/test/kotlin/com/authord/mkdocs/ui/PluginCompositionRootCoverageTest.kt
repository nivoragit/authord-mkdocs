package com.authord.mkdocs.ui

import com.authord.mkdocs.core.topic.TopicTreeMutationService
import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.InstanceRegistryPort
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ports.topic.TopicSyncOutcome
import com.authord.mkdocs.ports.topic.TopicSyncTransaction
import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TreeSyncOrchestrator
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ui.intellij.InstanceRegistryService
import com.authord.mkdocs.ui.intellij.InstanceRegistryStateStore
import com.authord.mkdocs.ui.intellij.PersistedInstanceRegistryState
import com.authord.mkdocs.ui.intellij.TopicTreeApplicationService
import com.authord.mkdocs.ui.intellij.TopicTreeUiService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PluginCompositionRootCoverageTest {
    @Test
    fun `default no-op gateways and orchestrator return unsupported failures`() {
        val wiring = PluginCompositionRoot().create(TopicTreeMutationService())
        val instance = topicInstance("default")

        assertUnsupported(wiring.mkDocsConfigGateway.loadConfig(instance))
        assertUnsupported(
            wiring.mkDocsConfigGateway.writeConfig(
                instance,
                document = com.authord.mkdocs.ports.topic.MkDocsConfigDocument(docsDir = "docs", nav = emptyList()),
            )
        )
        assertUnsupported(
            wiring.mkDocsConfigGateway.serializeDeterministically(
                com.authord.mkdocs.ports.topic.MkDocsConfigDocument(docsDir = "docs", nav = emptyList()),
            )
        )

        assertUnsupported(
            wiring.docsFileGateway.createMarkdownFile(
                instance = instance,
                relativePath = "docs/new-topic.md",
                initialContent = "# New Topic",
            )
        )
        assertUnsupported(
            wiring.docsFileGateway.deleteMarkdownFile(
                instance = instance,
                relativePath = "docs/new-topic.md",
                mode = TopicDeleteMode.RECOVERABLE,
            )
        )
        assertUnsupported(
            wiring.docsFileGateway.renameMarkdownFile(
                instance = instance,
                fromRelativePath = "docs/old-topic.md",
                toRelativePath = "docs/new-topic.md",
            )
        )
        assertUnsupported(
            wiring.docsFileGateway.moveMarkdownFile(
                instance = instance,
                fromRelativePath = "docs/source/topic.md",
                toRelativePath = "docs/target/topic.md",
            )
        )
        assertUnsupported(
            wiring.docsFileGateway.rewriteRelativeMarkdownLinks(
                instance = instance,
                fromRelativePath = "docs/source/topic.md",
                toRelativePath = "docs/target/topic.md",
            )
        )

        val transaction = topicTransaction(instance, commandId = "tx-default")
        assertUnsupported(wiring.treeSyncOrchestrator.apply(transaction))
        assertUnsupported(wiring.treeSyncOrchestrator.rollback(transaction.transactionId, "rollback-requested"))
        assertUnsupported(wiring.treeSyncOrchestrator.compensate(transaction.transactionId, "compensate-requested"))
    }

    @Test
    fun `default application service delegates execute rollback and active-instance lookup`() {
        val wiring = PluginCompositionRoot().create(TopicTreeMutationService())
        val instance = topicInstance("default")
        val transaction = topicTransaction(instance, commandId = "tx-app-service")

        assertUnsupported(wiring.applicationService.execute(transaction))
        assertUnsupported(wiring.applicationService.rollback(transaction.transactionId, "rollback"))

        val activeInstance = requireSuccess(wiring.applicationService.activeInstance())
        assertEquals(null, activeInstance)
    }

    @Test
    fun `instance registry service covers discovery registration and selection branches`() {
        val stateStore = object : InstanceRegistryStateStore {
            private var state: PersistedInstanceRegistryState? = null

            override fun load(projectKey: String): PersistedInstanceRegistryState? = state

            override fun save(projectKey: String, state: PersistedInstanceRegistryState) {
                this.state = state
            }
        }
        val registry = InstanceRegistryService(stateStore = stateStore)
        val projectRoot = Files.createTempDirectory("registry-default")
        Files.createDirectories(projectRoot.resolve("docs"))
        Files.writeString(
            projectRoot.resolve("mkdocs.yml"),
            "site_name: Demo\ndocs_dir: docs\n",
        )

        val discoveredDefault = requireSuccess(registry.discoverDefaultInstance(projectRoot.toString()))
        assertNotNull(discoveredDefault)
        assertEquals("default", discoveredDefault.instanceId)

        val discoveredAgain = requireSuccess(registry.discoverDefaultInstance(projectRoot.toString()))
        assertNotNull(discoveredAgain)

        val missingSelection = registry.selectActiveInstance("missing-instance")
        assertTrue(missingSelection is TopicGatewayResult.Failure)
        assertEquals(TopicSyncErrorCode.INSTANCE_SCOPE, missingSelection.error.code)

        val selectedDefault = requireSuccess(registry.selectActiveInstance(discoveredDefault.instanceId))
        assertEquals(discoveredDefault, selectedDefault)

        val listed = requireSuccess(registry.listInstances())
        assertTrue(listed.any { it.instanceId == discoveredDefault.instanceId })

        val isolatedStore = object : InstanceRegistryStateStore {
            private var state: PersistedInstanceRegistryState? = null

            override fun load(projectKey: String): PersistedInstanceRegistryState? = state

            override fun save(projectKey: String, state: PersistedInstanceRegistryState) {
                this.state = state
            }
        }
        val freshRegistry = InstanceRegistryService(stateStore = isolatedStore)
        val secondaryRoot = Files.createTempDirectory("registry-secondary")
        Files.createDirectories(secondaryRoot.resolve("docs"))
        val secondaryConfig = secondaryRoot.resolve("mkdocs.yml")
        Files.writeString(secondaryConfig, "site_name: Secondary\ndocs_dir: docs\n")
        val explicit = TopicInstanceRef(
            instanceId = "secondary",
            configPath = secondaryConfig.toString(),
            docsDirPath = secondaryRoot.resolve("docs").toString(),
        )
        requireSuccess(freshRegistry.registerInstance(explicit))
        assertEquals(explicit.instanceId, requireSuccess(freshRegistry.activeInstance())?.instanceId)

        val replacementRoot = Files.createTempDirectory("registry-replacement")
        Files.createDirectories(replacementRoot.resolve("docs"))
        val replacementConfig = replacementRoot.resolve("mkdocs.yml")
        Files.writeString(replacementConfig, "site_name: Replacement\ndocs_dir: docs\n")
        val replacement = TopicInstanceRef(
            instanceId = "replacement",
            configPath = replacementConfig.toString(),
            docsDirPath = replacementRoot.resolve("docs").toString(),
        )
        requireSuccess(freshRegistry.registerInstance(replacement))
        assertEquals(explicit.instanceId, requireSuccess(freshRegistry.activeInstance())?.instanceId)
    }

    @Test
    fun `default ui service dispatch refresh and instance-selection branches are covered`() {
        val activeInstance = topicInstance("active")
        val baseCommand = ValidateTopicTreeCommand(commandId = "cmd-dispatch", treeId = activeInstance.instanceId)

        val registryFailure = DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Registry unavailable")
        val uiWithRegistryFailure = newDefaultUiService(
            applicationService = RecordingApplicationService(),
            registry = StubInstanceRegistry(
                activeResult = TopicGatewayResult.Failure(registryFailure),
                selectResult = TopicGatewayResult.Failure(registryFailure),
            ),
        )
        assertFailureCode(uiWithRegistryFailure.dispatch(baseCommand), TopicSyncErrorCode.INSTANCE_SCOPE)
        assertFailureCode(uiWithRegistryFailure.refreshActiveTree(), TopicSyncErrorCode.INSTANCE_SCOPE)

        val uiWithoutActive = newDefaultUiService(
            applicationService = RecordingApplicationService(),
            registry = StubInstanceRegistry(
                activeResult = TopicGatewayResult.Success(null),
                selectResult = TopicGatewayResult.Failure(registryFailure),
            ),
        )
        val noActiveDispatch = uiWithoutActive.dispatch(baseCommand)
        assertFailureCode(noActiveDispatch, TopicSyncErrorCode.INSTANCE_SCOPE)
        assertTrue((noActiveDispatch as TopicGatewayResult.Failure).error.detail.contains("No active instance selected"))

        val noActiveRefresh = uiWithoutActive.refreshActiveTree()
        assertFailureCode(noActiveRefresh, TopicSyncErrorCode.INSTANCE_SCOPE)
        assertTrue((noActiveRefresh as TopicGatewayResult.Failure).error.detail.contains("No active instance selected"))

        val uiWithMismatch = newDefaultUiService(
            applicationService = RecordingApplicationService(),
            registry = StubInstanceRegistry(
                activeResult = TopicGatewayResult.Success(activeInstance),
                selectResult = TopicGatewayResult.Success(activeInstance),
            ),
        )
        val mismatch = uiWithMismatch.dispatch(
            ValidateTopicTreeCommand(commandId = "cmd-mismatch", treeId = "outside-scope")
        )
        assertFailureCode(mismatch, TopicSyncErrorCode.INSTANCE_SCOPE)
        assertTrue((mismatch as TopicGatewayResult.Failure).error.detail.contains("outside active instance"))

        val recordingApplication = RecordingApplicationService(
            executeResult = TopicGatewayResult.Success(
                TopicSyncOutcome(
                    transactionId = "tx-success",
                    applied = true,
                    rolledBack = false,
                    compensated = false,
                    message = "ok",
                )
            )
        )
        val successRegistry = StubInstanceRegistry(
            activeResult = TopicGatewayResult.Success(activeInstance),
            selectResult = TopicGatewayResult.Success(activeInstance),
        )
        val uiSuccess = newDefaultUiService(recordingApplication, successRegistry)

        val dispatchSuccess = uiSuccess.dispatch(baseCommand)
        assertTrue(dispatchSuccess is TopicGatewayResult.Success)
        assertEquals(1, recordingApplication.executedTransactions.size)
        val dispatchTransaction = recordingApplication.executedTransactions.first()
        assertEquals(baseCommand.commandId, dispatchTransaction.transactionId)
        assertEquals(activeInstance, dispatchTransaction.instance)
        assertEquals(baseCommand, dispatchTransaction.command)

        val refreshSuccess = uiSuccess.refreshActiveTree()
        assertTrue(refreshSuccess is TopicGatewayResult.Success)
        assertEquals(2, recordingApplication.executedTransactions.size)
        val refreshTransaction = recordingApplication.executedTransactions.last()
        val refreshCommand = refreshTransaction.command as ValidateTopicTreeCommand
        assertEquals("refresh-${activeInstance.instanceId}", refreshCommand.commandId)
        assertEquals(activeInstance.instanceId, refreshCommand.treeId)

        val selected = uiSuccess.selectInstance(activeInstance.instanceId)
        assertTrue(selected is TopicGatewayResult.Success)
        assertEquals(listOf(activeInstance.instanceId), successRegistry.selectedInstanceIds)
    }

    private fun topicInstance(id: String): TopicInstanceRef {
        return TopicInstanceRef(
            instanceId = id,
            configPath = "/tmp/$id/mkdocs.yml",
            docsDirPath = "/tmp/$id/docs",
        )
    }

    private fun topicTransaction(instance: TopicInstanceRef, commandId: String): TopicSyncTransaction {
        return TopicSyncTransaction(
            transactionId = commandId,
            instance = instance,
            command = ValidateTopicTreeCommand(commandId = commandId, treeId = instance.instanceId),
        )
    }

    private fun assertUnsupported(result: TopicGatewayResult<*>) {
        val failure = result as? TopicGatewayResult.Failure
        assertNotNull(failure)
        assertEquals(TopicSyncErrorCode.UNSUPPORTED, failure.error.code)
    }

    private fun assertFailureCode(result: TopicGatewayResult<*>, expectedCode: TopicSyncErrorCode) {
        val failure = result as? TopicGatewayResult.Failure
        assertNotNull(failure)
        assertEquals(expectedCode, failure.error.code)
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Expected success but was failure: ${result.error.code} ${result.error.detail}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun newDefaultUiService(
        applicationService: TopicTreeApplicationService,
        registry: InstanceRegistryPort,
    ): TopicTreeUiService {
        val type = Class.forName("com.authord.mkdocs.ui.DefaultTopicTreeUiService")
        val constructor = type.getDeclaredConstructor(
            TopicTreeApplicationService::class.java,
            InstanceRegistryPort::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(applicationService, registry) as TopicTreeUiService
    }

    private class RecordingApplicationService(
        private val executeResult: TopicGatewayResult<TopicSyncOutcome> = TopicGatewayResult.Success(
            TopicSyncOutcome(
                transactionId = "tx-recording",
                applied = true,
                rolledBack = false,
                compensated = false,
                message = "ok",
            )
        ),
        private val rollbackResult: TopicGatewayResult<TopicSyncOutcome> = TopicGatewayResult.Success(
            TopicSyncOutcome(
                transactionId = "rollback",
                applied = false,
                rolledBack = true,
                compensated = false,
                message = "rollback",
            )
        ),
        private val activeResult: TopicGatewayResult<TopicInstanceRef?> = TopicGatewayResult.Success(null),
    ) : TopicTreeApplicationService {
        val executedTransactions: MutableList<TopicSyncTransaction> = mutableListOf()

        override fun execute(transaction: TopicSyncTransaction): TopicGatewayResult<TopicSyncOutcome> {
            executedTransactions += transaction
            return executeResult
        }

        override fun rollback(transactionId: String, reason: String): TopicGatewayResult<TopicSyncOutcome> = rollbackResult

        override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> = activeResult
    }

    private class StubInstanceRegistry(
        var activeResult: TopicGatewayResult<TopicInstanceRef?>,
        var selectResult: TopicGatewayResult<TopicInstanceRef>,
    ) : InstanceRegistryPort {
        val selectedInstanceIds: MutableList<String> = mutableListOf()

        override fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?> {
            return TopicGatewayResult.Success(null)
        }

        override fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit> = TopicGatewayResult.Success(Unit)

        override fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>> = TopicGatewayResult.Success(emptyList())

        override fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef> {
            selectedInstanceIds += instanceId
            return selectResult
        }

        override fun activeInstance(): TopicGatewayResult<TopicInstanceRef?> = activeResult
    }
}
