package com.authord.mkdocs.defaults.command

import com.authord.mkdocs.ports.command.CommandRegistry
import com.authord.mkdocs.ports.command.TopicTreeCommandHandler
import java.util.concurrent.ConcurrentHashMap

class InMemoryCommandRegistry : CommandRegistry {
    private val handlers = ConcurrentHashMap<String, TopicTreeCommandHandler>()

    override fun register(commandType: String, handler: TopicTreeCommandHandler) {
        handlers[commandType] = handler
    }

    override fun resolve(commandType: String): TopicTreeCommandHandler? = handlers[commandType]
}
