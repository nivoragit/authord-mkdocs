package com.authord.mkdocs.ports.topic

/**
 * Port for parsing and deterministic serialization of MkDocs configuration.
 *
 * Usage contract:
 * - Inputs: [TopicInstanceRef] and [MkDocsConfigDocument] values.
 * - Outputs: parsed documents, serialized YAML, or typed failures.
 * - Errors: [TopicSyncErrorCode.CONFIG_PARSE], [TopicSyncErrorCode.CONFIG_WRITE], and
 *   [TopicSyncErrorCode.VALIDATION].
 *
 * Usage example:
 * ```kotlin
 * val loaded = gateway.loadConfig(instance)
 * if (loaded is TopicGatewayResult.Success) {
 *     gateway.writeConfig(instance, loaded.value)
 * }
 * ```
 */
interface MkDocsConfigGateway {
    /**
     * Loads the active instance config and returns normalized document data.
     *
     * Input: active [instance].
     * Output: [TopicGatewayResult.Success] with [MkDocsConfigDocument] when parse succeeds.
     * Errors/failure modes: CONFIG_PARSE for missing/malformed files, VALIDATION for invalid values.
     */
    fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument>

    /**
     * Writes config content for the active instance.
     *
     * Inputs: active [instance] and normalized [document].
     * Output: [TopicGatewayResult.Success] with [Unit] when write completes.
     * Errors/failure modes: CONFIG_WRITE when persistence fails, VALIDATION for invalid document payload.
     */
    fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit>

    /**
     * Serializes the config deterministically to prevent diff churn.
     *
     * Input: normalized [document].
     * Output: [TopicGatewayResult.Success] containing deterministic YAML content.
     * Errors/failure modes: CONFIG_WRITE when deterministic serialization fails.
     */
    fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String>
}
