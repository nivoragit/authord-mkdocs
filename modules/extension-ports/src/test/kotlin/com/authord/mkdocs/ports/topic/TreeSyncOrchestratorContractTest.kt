package com.authord.mkdocs.ports.topic

import kotlin.test.Test
import kotlin.test.assertTrue

class TreeSyncOrchestratorContractTest {
    @Test
    fun `defines apply rollback and compensate contract`() {
        val methods = TreeSyncOrchestrator::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("apply"))
        assertTrue(methods.contains("rollback"))
        assertTrue(methods.contains("compensate"))
    }

    @Test
    fun `sync transaction requires command and instance context`() {
        val command = ValidateTopicTreeCommand(commandId = "cmd-1", treeId = "tree-1")
        val instance = TopicInstanceRef(instanceId = "default", configPath = "/repo/mkdocs.yml", docsDirPath = "/repo/docs")
        val transaction = TopicSyncTransaction(transactionId = "tx-1", instance = instance, command = command)

        assertTrue(transaction.transactionId.isNotBlank())
        assertTrue(transaction.instance.instanceId.isNotBlank())
        assertTrue(transaction.command.commandId.isNotBlank())
    }
}
