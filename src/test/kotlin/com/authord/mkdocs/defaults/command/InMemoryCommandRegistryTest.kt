package com.authord.mkdocs.defaults.command

import com.authord.mkdocs.ports.command.TopicTreeCommandHandler
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InMemoryCommandRegistryTest {
    @Test
    fun `register and resolve handler by command type`() {
        val registry = InMemoryCommandRegistry()
        val handler = TopicTreeCommandHandler {
            TopicTreeCommandResult(
                commandId = it.commandId,
                status = TopicTreeCommandStatus.SUCCESS,
                treeVersion = 1,
            )
        }

        registry.register("VALIDATE", handler)
        val resolved = registry.resolve("VALIDATE")

        assertNotNull(resolved)
        val result = resolved.handle(ValidateTopicTreeCommand("c1", "t1"))
        assertEquals(TopicTreeCommandStatus.SUCCESS, result.status)
        assertEquals(1, result.treeVersion)
    }
}
