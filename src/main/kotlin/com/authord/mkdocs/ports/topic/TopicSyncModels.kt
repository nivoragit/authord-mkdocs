package com.authord.mkdocs.ports.topic

/**
 * Identifies a single MkDocs instance scope used for tree, file, and config mutations.
 */
data class TopicInstanceRef(
    val instanceId: String,
    val configPath: String,
    val docsDirPath: String,
)

/**
 * Represents one navigation node persisted in `mkdocs.yml` nav.
 */
data class TopicNavNode(
    val nodeId: String,
    val title: String,
    val path: String? = null,
    val externalUrl: String? = null,
    val children: List<TopicNavNode> = emptyList(),
)

/**
 * In-memory representation of parsed MkDocs config shape used by Phase 2 sync flows.
 */
data class MkDocsConfigDocument(
    val docsDir: String,
    val nav: List<TopicNavNode>,
    val notInNav: List<String> = emptyList(),
    val navPresent: Boolean = true,
    val siteName: String? = null,
    val rawYaml: Map<String, Any?> = emptyMap(),
)

/**
 * File operation kinds synchronized with tree/nav mutations.
 */
enum class TopicFileOperationKind {
    CREATE,
    DELETE,
    RENAME,
    MOVE,
    REWRITE_LINKS,
}

/**
 * Delete modes for markdown-backed topics.
 */
enum class TopicDeleteMode {
    RECOVERABLE,
    NAV_ONLY,
}

/**
 * One file operation in an orchestrated sync transaction.
 */
data class TopicFileOperation(
    val kind: TopicFileOperationKind,
    val sourcePath: String,
    val targetPath: String? = null,
)

/**
 * Cross-surface transaction payload for tree/nav/file synchronization.
 */
data class TopicSyncTransaction(
    val transactionId: String,
    val instance: TopicInstanceRef,
    val command: TopicTreeCommand,
    val fileOperations: List<TopicFileOperation> = emptyList(),
)

/**
 * Final transaction outcome reported by orchestration and service APIs.
 */
data class TopicSyncOutcome(
    val transactionId: String,
    val applied: Boolean,
    val rolledBack: Boolean,
    val compensated: Boolean,
    val message: String,
    val appliedFileOperations: List<TopicFileOperation> = emptyList(),
)
