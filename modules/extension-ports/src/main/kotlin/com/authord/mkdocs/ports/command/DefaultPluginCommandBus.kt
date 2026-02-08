package com.authord.mkdocs.ports.command

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicTreeViolation

class DefaultPluginCommandBus(
    private val commandRegistry: CommandRegistry,
) : PluginCommandBus {
    override fun dispatch(command: TopicTreeCommand): TopicTreeCommandResult {
        val handler = commandRegistry.resolve(command.commandType.name)
        if (handler == null) {
            return TopicTreeCommandResult(
                commandId = command.commandId,
                status = TopicTreeCommandStatus.REJECTED,
                violations = listOf(TopicTreeViolation("NO_HANDLER", "No handler registered for ${command.commandType}")),
                message = "Command rejected",
            )
        }
        return handler.handle(command)
    }
}
