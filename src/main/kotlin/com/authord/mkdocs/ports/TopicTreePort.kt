package com.authord.mkdocs.ports

import com.authord.mkdocs.ports.topic.TopicTreeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult

/** API version for [TopicTreePort]. */
const val TOPIC_TREE_PORT_API_VERSION: String = "2.0.0"

/** API version for `TopicTreeApplicationService` public boundary. */
const val TOPIC_TREE_APP_SERVICE_API_VERSION: String = "2.0.0"

/** API version for `TopicTreeUiService` public boundary. */
const val TOPIC_TREE_UI_SERVICE_API_VERSION: String = "2.0.0"

/**
 * Centralized version registry for Phase 2 topic-tree public surfaces.
 */
data class TopicTreeApiVersionRegistry(
    val port: String = TOPIC_TREE_PORT_API_VERSION,
    val applicationService: String = TOPIC_TREE_APP_SERVICE_API_VERSION,
    val uiService: String = TOPIC_TREE_UI_SERVICE_API_VERSION,
)

/** Default public API version registry instance. */
val TOPIC_TREE_API_VERSION_REGISTRY: TopicTreeApiVersionRegistry = TopicTreeApiVersionRegistry()

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
