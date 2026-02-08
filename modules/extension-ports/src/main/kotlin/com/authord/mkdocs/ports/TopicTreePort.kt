package com.authord.mkdocs.ports

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult

interface TopicTreePort {
    fun execute(command: TopicTreeCommand): TopicTreeCommandResult
}
