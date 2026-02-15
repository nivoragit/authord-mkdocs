package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * YAML-backed implementation of [MkDocsConfigGateway] with deterministic serialization.
 */
class MkDocsYamlGateway : MkDocsConfigGateway {
    /**
     * Loads and normalizes MkDocs config content for the active instance.
     */
    override fun loadConfig(instance: TopicInstanceRef): TopicGatewayResult<MkDocsConfigDocument> {
        val configPath = Paths.get(instance.configPath)
        if (!Files.exists(configPath)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.CONFIG_PARSE, "Config file not found: ${instance.configPath}"),
            )
        }

        return runCatching {
            val root = Files.newBufferedReader(configPath).use { reader ->
                Yaml().load<Any?>(reader)
            }

            val map = (root as? Map<*, *>) ?: emptyMap<String, Any>()
            val docsDir = map["docs_dir"]?.toString()?.trim().orEmpty().ifBlank { "docs" }
            val nav = parseNav(map["nav"], "n")
            val notInNav = parseStringList(map["not_in_nav"])
            MkDocsConfigDocument(
                docsDir = docsDir,
                nav = nav,
                notInNav = notInNav,
            )
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(
                        TopicSyncErrorCode.CONFIG_PARSE,
                        "Failed to parse config ${instance.configPath}: ${it.message}",
                    ),
                )
            },
        )
    }

    /**
     * Persists normalized config content using deterministic serialization output.
     */
    override fun writeConfig(instance: TopicInstanceRef, document: MkDocsConfigDocument): TopicGatewayResult<Unit> {
        val configPath = Paths.get(instance.configPath)
        val serialized = when (val result = serializeDeterministically(document)) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> return result
        }

        return runCatching {
            Files.createDirectories(configPath.parent ?: Paths.get("."))
            val previous = if (Files.exists(configPath)) Files.readString(configPath) else null
            if (previous != serialized) {
                Files.writeString(configPath, serialized)
            }
        }.fold(
            onSuccess = { TopicGatewayResult.Success(Unit) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(
                        TopicSyncErrorCode.CONFIG_WRITE,
                        "Failed to write config ${instance.configPath}: ${it.message}",
                    ),
                )
            },
        )
    }

    /**
     * Produces canonical YAML text for equivalent logical config structures.
     */
    override fun serializeDeterministically(document: MkDocsConfigDocument): TopicGatewayResult<String> {
        return runCatching {
            // Use linked insertion order so equivalent logical content serializes with stable key
            // ordering across repeated save cycles.
            val root = linkedMapOf<String, Any>(
                "docs_dir" to document.docsDir,
                "nav" to serializeNav(document.nav),
            )
            if (document.notInNav.isNotEmpty()) {
                // Normalize + sort to avoid churn from path separator or input ordering variance.
                root["not_in_nav"] = document.notInNav
                    .map { normalizePath(it) }
                    .distinct()
                    .sorted()
            }

            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
                lineBreak = DumperOptions.LineBreak.UNIX
                indent = 2
                isPrettyFlow = true
                width = 160
            }

            Yaml(options).dump(root).trimEnd() + "\n"
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(
                        TopicSyncErrorCode.CONFIG_WRITE,
                        "Failed deterministic serialization: ${it.message}",
                    ),
                )
            },
        )
    }

    private fun parseNav(rawNav: Any?, idPrefix: String): List<TopicNavNode> {
        val entries = rawNav as? List<*> ?: return emptyList()
        return entries.mapIndexedNotNull { index, rawEntry ->
            parseNavEntry(rawEntry, "$idPrefix-$index")
        }
    }

    private fun parseNavEntry(rawEntry: Any?, nodeId: String): TopicNavNode? {
        return when (rawEntry) {
            is Map<*, *> -> {
                val first = rawEntry.entries.firstOrNull() ?: return null
                val title = first.key?.toString()?.trim().orEmpty().ifBlank { return null }
                when (val value = first.value) {
                    is String -> {
                        val normalized = normalizePath(value)
                        val external = normalized.takeIf { isExternalUrl(it) }
                        TopicNavNode(
                            nodeId = nodeId,
                            title = title,
                            path = if (external == null) normalized else null,
                            externalUrl = external,
                            children = emptyList(),
                        )
                    }

                    is List<*> -> TopicNavNode(
                        nodeId = nodeId,
                        title = title,
                        children = parseNav(value, nodeId),
                    )

                    else -> TopicNavNode(
                        nodeId = nodeId,
                        title = title,
                        children = emptyList(),
                    )
                }
            }

            is String -> {
                val normalized = normalizePath(rawEntry)
                TopicNavNode(
                    nodeId = nodeId,
                    title = deriveTitleFromPath(normalized),
                    path = normalized,
                    children = emptyList(),
                )
            }

            else -> null
        }
    }

    private fun parseStringList(raw: Any?): List<String> {
        val values = raw as? List<*> ?: return emptyList()
        return values.mapNotNull { value ->
            value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizePath)
        }
    }

    private fun serializeNav(nodes: List<TopicNavNode>): List<Map<String, Any>> {
        // Preserve nav order from the domain model; deterministic ordering comes from caller state.
        return nodes.map { node ->
            val value: Any = when {
                node.externalUrl != null -> node.externalUrl!!
                node.children.isNotEmpty() -> serializeNav(node.children)
                else -> normalizePath(node.path.orEmpty())
            }
            linkedMapOf(node.title to value)
        }
    }

    private fun deriveTitleFromPath(path: String): String {
        val fileName = Paths.get(path).fileName?.toString().orEmpty().ifBlank { path }
        return fileName.removeSuffix(".md").replace('-', ' ').replace('_', ' ').trim().ifBlank { "Untitled" }
    }

    private fun isExternalUrl(pathOrUrl: String): Boolean {
        return pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim()
    }
}
