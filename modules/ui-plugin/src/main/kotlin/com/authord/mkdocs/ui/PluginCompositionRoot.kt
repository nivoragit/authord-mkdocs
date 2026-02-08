package com.authord.mkdocs.ui

import com.authord.mkdocs.defaults.command.InMemoryCommandRegistry
import com.authord.mkdocs.defaults.preview.NoOpPreviewSyncAdapter
import com.authord.mkdocs.defaults.vector.NoOpVectorStoreAdapter
import com.authord.mkdocs.ports.TopicTreePort
import com.authord.mkdocs.ports.command.DefaultPluginCommandBus
import com.authord.mkdocs.ports.command.PluginCommandBus
import com.authord.mkdocs.ports.preview.PreviewSyncPort
import com.authord.mkdocs.ports.vector.VectorStorePort

class PluginCompositionRoot {
    data class Wiring(
        val commandBus: PluginCommandBus,
        val previewSyncPort: PreviewSyncPort,
        val vectorStorePort: VectorStorePort,
        val commandRegistry: InMemoryCommandRegistry,
    )

    fun create(topicTreePort: TopicTreePort): Wiring {
        val registry = InMemoryCommandRegistry()
        registry.register("ADD") { topicTreePort.execute(it) }
        registry.register("MOVE") { topicTreePort.execute(it) }
        registry.register("REMOVE") { topicTreePort.execute(it) }
        registry.register("REORDER") { topicTreePort.execute(it) }
        registry.register("VALIDATE") { topicTreePort.execute(it) }

        return Wiring(
            commandBus = DefaultPluginCommandBus(registry),
            previewSyncPort = NoOpPreviewSyncAdapter(),
            vectorStorePort = NoOpVectorStoreAdapter(),
            commandRegistry = registry,
        )
    }
}
