package com.authord.mkdocs.ports.topic

/**
 * Error categories for topic-tree synchronization APIs.
 */
enum class TopicSyncErrorCode {
    VALIDATION,
    CONFIG_PARSE,
    CONFIG_WRITE,
    FILE_IO,
    RECONCILIATION,
    ORCHESTRATION,
    INSTANCE_SCOPE,
    UNSUPPORTED,
}

/**
 * Structured sync error payload shared by ports and orchestrators.
 */
interface TopicSyncError {
    val code: TopicSyncErrorCode
    val detail: String
}

/**
 * Default immutable sync error implementation.
 */
data class DefaultTopicSyncError(
    override val code: TopicSyncErrorCode,
    override val detail: String,
) : TopicSyncError

/**
 * Result envelope for topic-tree gateway and orchestration APIs.
 */
sealed class TopicGatewayResult<out T> {
    /**
     * Successful result with typed payload.
     */
    data class Success<T>(val value: T) : TopicGatewayResult<T>()

    /**
     * Failed result with typed sync error.
     */
    data class Failure(val error: TopicSyncError) : TopicGatewayResult<Nothing>()
}
