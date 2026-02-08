package com.authord.mkdocs.defaults.command

import com.authord.mkdocs.ports.command.CommandRegistry
import com.authord.mkdocs.ports.command.TopicTreeCommandHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory command registry for MVP/default usage.
 */
class InMemoryCommandRegistry : CommandRegistry {
    private val handlers = ConcurrentHashMap<String, TopicTreeCommandHandler>()

    /** Registers or replaces a command handler by command type key. */
    override fun register(commandType: String, handler: TopicTreeCommandHandler) {
        handlers[commandType] = handler
    }

    /** Resolves a command handler by type key. */
    override fun resolve(commandType: String): TopicTreeCommandHandler? = handlers[commandType]
}
