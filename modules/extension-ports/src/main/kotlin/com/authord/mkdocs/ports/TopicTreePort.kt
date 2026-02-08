package com.authord.mkdocs.ports

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult

/** API version for [TopicTreePort]. */
const val TOPIC_TREE_PORT_API_VERSION: String = "1.0.0"

/**
 * Public topic-tree mutation boundary.
 *
 * API Version: [TOPIC_TREE_PORT_API_VERSION]
 *
 * Contract:
 * - Accepts typed [TopicTreeCommand] inputs.
 * - Returns typed [TopicTreeCommandResult] outputs.
 * - Implementations must enforce aggregate validation and deterministic results.
 */
interface TopicTreePort {
    /**
     * Executes a topic-tree command.
     */
    fun execute(command: TopicTreeCommand): TopicTreeCommandResult
}
