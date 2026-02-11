package com.authord.mkdocs.ui

import com.authord.mkdocs.defaults.command.InMemoryCommandRegistry
import com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter
import com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.command.DefaultPluginCommandBus
import com.authord.mkdocs.ports.command.PluginCommandBus
import com.authord.mkdocs.ports.preview.PreviewSyncPort
import com.authord.mkdocs.ports.topic.TopicTreeCommandType
import com.authord.mkdocs.ports.vector.VectorStorePort
import com.authord.mkdocs.ui.intellij.PluginRuntimeIntegrationService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Composition root that wires default MVP command bus and seam adapters.
 */
class PluginCompositionRoot {
    /**
     * Wiring bundle returned to plugin entry points.
     */
    data class Wiring(
        val commandBus: PluginCommandBus,
        val previewSyncPort: PreviewSyncPort,
        val vectorStorePort: VectorStorePort,
        val commandRegistry: InMemoryCommandRegistry,
    )

    /**
     * Creates default wiring for topic-tree command dispatch and seam adapters.
     */
    fun create(topicTreePort: TopicTreePort): Wiring {
        val registry = InMemoryCommandRegistry()
        val commandTypes = listOf(
            TopicTreeCommandType.ADD,
            TopicTreeCommandType.MOVE,
            TopicTreeCommandType.REMOVE,
            TopicTreeCommandType.RENAME,
            TopicTreeCommandType.REPARENT,
            TopicTreeCommandType.REORDER,
            TopicTreeCommandType.VALIDATE,
        )

        commandTypes.forEach { type ->
            registry.register(type.name) { topicTreePort.execute(it) }
        }

        return Wiring(
            commandBus = DefaultPluginCommandBus(registry),
            previewSyncPort = NoOpPreviewSyncAdapter(),
            vectorStorePort = NoOpVectorStoreAdapter(),
            commandRegistry = registry,
        )
    }

    /**
     * Resolves project-scoped IntelliJ runtime integration wiring for shell entry points.
     */
    fun runtimeIntegration(project: Project): PluginRuntimeIntegrationService = project.service()
}
