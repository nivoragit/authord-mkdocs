package com.authord.mkdocs.ports.command

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult

fun interface TopicTreeCommandHandler {
    fun handle(command: TopicTreeCommand): TopicTreeCommandResult
}

interface CommandRegistry {
    fun register(commandType: String, handler: TopicTreeCommandHandler)
    fun resolve(commandType: String): TopicTreeCommandHandler?
}

interface PluginCommandBus {
    fun dispatch(command: TopicTreeCommand): TopicTreeCommandResult
}
