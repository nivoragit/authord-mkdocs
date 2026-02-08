package com.authord.mkdocs.ports

import com.authord.mkdocs.defaults.command.InMemoryCommandRegistry
import com.authord.mkdocs.ports.command.DefaultPluginCommandBus
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginCommandBusContractTest {
    @Test
    fun `dispatches command to registered handler`() {
        val registry = InMemoryCommandRegistry()
        registry.register("VALIDATE") {
            TopicTreeCommandResult(
                commandId = it.commandId,
                status = TopicTreeCommandStatus.SUCCESS,
                treeVersion = 2,
            )
        }

        val bus = DefaultPluginCommandBus(registry)
        val result = bus.dispatch(ValidateTopicTreeCommand("cmd-1", "tree-1"))

        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        assertEquals(2, result.treeVersion)
    }

    @Test
    fun `returns rejected result when no handler exists`() {
        val bus = DefaultPluginCommandBus(InMemoryCommandRegistry())

        val result = bus.dispatch(ValidateTopicTreeCommand("cmd-2", "tree-1"))

        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
    }
}
