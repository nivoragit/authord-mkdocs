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

/**
 * Optional extension port for forcing aggregate state replacement from nav snapshots.
 *
 * Intended for reconciliation flows where the backing tree must mirror current disk/config
 * state even after prior mutations have populated in-memory nodes.
 */
interface TopicTreeAggregateRefreshPort {
    /**
     * Replaces aggregate backing [treeId] with a new tree hydrated from [nav].
     */
    fun refreshTreeFromNav(treeId: String, nav: List<TopicNavNode>)
}
