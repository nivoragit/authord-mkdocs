package com.authord.mkdocs.ports.command

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult

/** API version for command-bus and registry contracts. */
const val COMMAND_BUS_API_VERSION: String = "1.0.0"

/**
 * Handler contract for one topic-tree command dispatch.
 *
 * API Version: [COMMAND_BUS_API_VERSION]
 */
fun interface TopicTreeCommandHandler {
    /** Handles one command instance. */
    fun handle(command: TopicTreeCommand): TopicTreeCommandResult
}

/**
 * Registry contract mapping command type identifiers to handlers.
 *
 * API Version: [COMMAND_BUS_API_VERSION]
 *
 * Constraints:
 * - `commandType` keys are expected to align with `TopicTreeCommandType.name`.
 * - Missing handlers should resolve as `null` and be handled by dispatch policy.
 */
interface CommandRegistry {
    /** Registers or replaces a handler for a command type key. */
    fun register(commandType: String, handler: TopicTreeCommandHandler)

    /** Resolves a handler by command type key. */
    fun resolve(commandType: String): TopicTreeCommandHandler?
}

/**
 * Bus contract for routing topic-tree commands through a registry.
 *
 * API Version: [COMMAND_BUS_API_VERSION]
 */
interface PluginCommandBus {
    /** Dispatches one command and returns the resolved handler result. */
    fun dispatch(command: TopicTreeCommand): TopicTreeCommandResult
}
