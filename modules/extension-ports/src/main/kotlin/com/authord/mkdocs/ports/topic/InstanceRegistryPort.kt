package com.authord.mkdocs.ports.topic

/**
 * Port for multi-instance registration, discovery, and active-instance selection.
 *
 * Usage contract:
 * - Inputs: project root paths and [TopicInstanceRef] registrations.
 * - Outputs: active/default/registered instance views.
 * - Errors: [TopicSyncErrorCode.INSTANCE_SCOPE] and [TopicSyncErrorCode.VALIDATION].
 *
 * Usage example:
 * ```kotlin
 * registry.discoverDefaultInstance(projectRoot)
 * registry.registerInstance(extraInstance)
 * registry.selectActiveInstance(extraInstance.instanceId)
 * ```
 */
interface InstanceRegistryPort {
    /**
     * Discovers root-level MkDocs config as the default instance.
     *
     * Input: [projectRootPath] for active project.
     * Output: [TopicGatewayResult.Success] with discovered default or null when none exists.
     * Errors/failure modes: VALIDATION for invalid project root input.
     */
    fun discoverDefaultInstance(projectRootPath: String): TopicGatewayResult<TopicInstanceRef?>

    /**
     * Registers a non-default instance provided by explicit config path.
     *
     * Input: explicit [instance] registration payload.
     * Output: [TopicGatewayResult.Success] with [Unit] when registration is accepted.
     * Errors/failure modes: VALIDATION for malformed config path or invalid instance data.
     */
    fun registerInstance(instance: TopicInstanceRef): TopicGatewayResult<Unit>

    /**
     * Lists all registered instances.
     *
     * Input: none.
     * Output: [TopicGatewayResult.Success] with registered instances.
     * Errors/failure modes: INSTANCE_SCOPE if registry state cannot be resolved for project context.
     */
    fun listInstances(): TopicGatewayResult<List<TopicInstanceRef>>

    /**
     * Selects and returns the active instance.
     *
     * Input: requested [instanceId].
     * Output: [TopicGatewayResult.Success] with selected instance.
     * Errors/failure modes: INSTANCE_SCOPE for unknown or unavailable instances.
     */
    fun selectActiveInstance(instanceId: String): TopicGatewayResult<TopicInstanceRef>

    /**
     * Returns currently active instance, if any.
     *
     * Input: none.
     * Output: [TopicGatewayResult.Success] with active instance or null.
     * Errors/failure modes: INSTANCE_SCOPE when active context cannot be resolved.
     */
    fun activeInstance(): TopicGatewayResult<TopicInstanceRef?>
}
