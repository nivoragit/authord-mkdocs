package com.authord.mkdocs.ports.command

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicTreeViolation

/**
 * Default command bus implementation backed by a [CommandRegistry].
 */
class DefaultPluginCommandBus(
    private val commandRegistry: CommandRegistry,
) : PluginCommandBus {
    /**
     * Dispatches command to a registered handler.
     *
     * Returns a rejected result with `NO_HANDLER` when command type has no registration.
     */
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
