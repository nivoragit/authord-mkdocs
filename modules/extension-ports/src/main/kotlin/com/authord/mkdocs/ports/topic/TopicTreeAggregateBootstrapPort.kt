package com.authord.mkdocs.ports.topic

/**
 * Optional capability for seeding topic-tree aggregate state from persisted MkDocs nav content.
 *
 * Implementations should treat this as an idempotent bootstrap operation and avoid destructive
 * resets when the aggregate is already initialized for the target tree.
 */
interface TopicTreeAggregateBootstrapPort {
    /**
     * Populates the aggregate backing [treeId] using [nav] nodes loaded from `mkdocs.yml`.
     */
    fun bootstrapTreeFromNav(treeId: String, nav: List<TopicNavNode>)
}
