package com.authord.mkdocs.core.topic

/**
 * Stable topic identifier value object for provider-agnostic domain usage.
 */
@JvmInline
value class TopicId(val value: String) {
    init {
        require(value.isNotBlank()) { "TopicId must not be blank" }
    }
}

/**
 * Stable topic ordering value object.
 */
@JvmInline
value class TopicOrder(val value: Int) {
    init {
        require(value >= 0) { "TopicOrder must be >= 0" }
    }
}

/**
 * Relationship between two topics for navigation and parity adapters.
 */
data class TopicLink(
    val from: TopicId,
    val to: TopicId,
    val relation: String = "child",
)

/**
 * Extensible metadata envelope for topic nodes.
 */
data class TopicMetadata(
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * Compatibility alias representing the topic-tree aggregate model.
 */
typealias TopicTree = TopicTreeAggregate
