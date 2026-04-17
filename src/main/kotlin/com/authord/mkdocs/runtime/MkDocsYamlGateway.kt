package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.MkDocsConfigGateway
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ui.intellij.resolveImplicitDocsDirPath
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.inspector.TagInspector
import org.yaml.snakeyaml.representer.Represent
import org.yaml.snakeyaml.representer.Representer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * YAML-backed implementation of [MkDocsConfigGateway] with deterministic serialization.
 */
class MkDocsYamlGateway : MkDocsConfigGateway {
    private data class TaggedYamlValue(
        val tag: String,
        val value: Any?,
    )

    private class TaggedYamlRepresenter(options: DumperOptions) : Representer(options) {
        init {
            representers[TaggedYamlValue::class.java] = RepresentTaggedYamlValue()
        }

        private inner class RepresentTaggedYamlValue : Represent {
            override fun representData(data: Any?): Node {
                val tagged = data as TaggedYamlValue
                val tag = Tag(tagged.tag)
                return when (val value = tagged.value) {
                    is Map<*, *> -> representMapping(tag, value, DumperOptions.FlowStyle.BLOCK)
                    is Iterable<*> -> representSequence(tag, value, DumperOptions.FlowStyle.BLOCK)
                    else -> representScalar(tag, value?.toString() ?: "")
                }
            }
        }
    }

    private class StandardScalarConstructor(loaderOptions: LoaderOptions) : SafeConstructor(loaderOptions) {
        fun constructStandardScalar(node: ScalarNode): Any? = constructObject(node)
    }

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
            val loaderOptions = LoaderOptions().apply {
                tagInspector = TagInspector { true }
            }
            val scalarConstructor = StandardScalarConstructor(loaderOptions)
            val root = Files.newBufferedReader(configPath).use { reader ->
                Yaml(loaderOptions).compose(reader)
            }

            val map = (root?.let { convertNode(it, scalarConstructor) } as? Map<*, *>) ?: emptyMap<String, Any>()
            val rawYaml = linkedMapOf<String, Any?>()
            map.forEach { (key, value) ->
                key?.toString()?.let { normalizedKey ->
                    rawYaml[normalizedKey] = value
                }
            }
            val docsDir = unwrapTaggedScalar(map["docs_dir"])?.toString()?.trim().orEmpty().ifBlank {
                normalizeExistingDocsDirPath(instance.docsDirPath, configPath)
            }
            val siteName = unwrapTaggedScalar(map["site_name"])?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val navPresent = map.containsKey("nav")
            val nav = parseNav(map["nav"], "n")
            val notInNav = parseStringList(map["not_in_nav"])
            MkDocsConfigDocument(
                docsDir = docsDir,
                nav = nav,
                notInNav = notInNav,
                navPresent = navPresent,
                siteName = siteName,
                rawYaml = rawYaml,
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
        val serialized = when (val result = serializeDeterministically(document, configPath)) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> return result
        }

        return runCatching {
            Files.createDirectories(configPath.parent ?: Paths.get("."))
            val previous = if (Files.exists(configPath)) Files.readString(configPath) else null
            if (previous != serialized) {
                val parent = configPath.parent ?: Paths.get(".")
                val tempFile = Files.createTempFile(parent, ".${configPath.fileName}.", ".tmp")
                try {
                    Files.writeString(tempFile, serialized)
                    try {
                        Files.move(
                            tempFile,
                            configPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            tempFile,
                            configPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                } finally {
                    Files.deleteIfExists(tempFile)
                }
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
        return serializeDeterministically(document, configPath = null)
    }

    private fun serializeDeterministically(
        document: MkDocsConfigDocument,
        configPath: Path?,
    ): TopicGatewayResult<String> {
        return runCatching {
            // Use linked insertion order so equivalent logical content serializes with stable key
            // ordering across repeated save cycles.
            val root = linkedMapOf<String, Any?>().apply {
                putAll(document.rawYaml)
            }
            document.siteName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { root["site_name"] = it }
                ?: root.remove("site_name")
            if (shouldPreserveImplicitDocsDir(document, configPath)) {
                root.remove("docs_dir")
            } else {
                root["docs_dir"] = document.docsDir.trim().ifBlank { "docs" }
            }
            if (document.navPresent) {
                root["nav"] = serializeNav(document.nav)
            } else {
                root.remove("nav")
            }
            if (document.notInNav.isNotEmpty()) {
                // Normalize + sort to avoid churn from path separator or input ordering variance.
                root["not_in_nav"] = document.notInNav
                    .map { normalizePath(it) }
                    .distinct()
                    .sorted()
            } else {
                root.remove("not_in_nav")
            }

            val options = DumperOptions().apply {
                defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
                lineBreak = DumperOptions.LineBreak.UNIX
                indent = 2
                isPrettyFlow = true
                width = 160
            }

            val representer = TaggedYamlRepresenter(options)
            Yaml(representer, options).dump(root).trimEnd() + "\n"
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
                when (val value = unwrapTaggedScalar(first.value)) {
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

    private fun shouldPreserveImplicitDocsDir(document: MkDocsConfigDocument, configPath: Path?): Boolean {
        if (configPath == null || document.rawYaml.containsKey("docs_dir")) {
            return false
        }
        return resolveDocumentDocsDirPath(configPath, document.docsDir) == resolveImplicitDocsDirPath(configPath)
    }

    private fun resolveDocumentDocsDirPath(configPath: Path, rawDocsDir: String): Path {
        val trimmed = rawDocsDir.trim()
        if (trimmed.isBlank()) {
            return resolveImplicitDocsDirPath(configPath)
        }
        val candidate = runCatching { Paths.get(trimmed) }.getOrNull() ?: return resolveImplicitDocsDirPath(configPath)
        val absolute = if (candidate.isAbsolute) candidate else configPath.parent.resolve(candidate)
        return absolute.toAbsolutePath().normalize()
    }

    private fun normalizeExistingDocsDirPath(rawDocsDirPath: String, configPath: Path): String {
        val resolved = runCatching { Paths.get(rawDocsDirPath).toAbsolutePath().normalize() }.getOrNull()
            ?: resolveImplicitDocsDirPath(configPath)
        return resolved.toString().replace('\\', '/')
    }

    private fun parseStringList(raw: Any?): List<String> {
        val values = raw as? List<*> ?: return emptyList()
        return values.mapNotNull { value ->
            unwrapTaggedScalar(value)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizePath)
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

    private fun convertNode(node: Node, scalarConstructor: StandardScalarConstructor): Any? {
        return when (node) {
            is MappingNode -> {
                val map = linkedMapOf<String, Any?>()
                node.value.forEach { tuple ->
                    val key = convertNodeKey(tuple.keyNode, scalarConstructor) ?: return@forEach
                    map[key] = convertNode(tuple.valueNode, scalarConstructor)
                }
                if (Tag.standardTags.contains(node.tag)) {
                    map
                } else {
                    TaggedYamlValue(node.tag.value, map)
                }
            }

            is SequenceNode -> {
                val sequence = node.value.map { child -> convertNode(child, scalarConstructor) }
                if (Tag.standardTags.contains(node.tag)) {
                    sequence
                } else {
                    TaggedYamlValue(node.tag.value, sequence)
                }
            }

            is ScalarNode -> {
                if (!Tag.standardTags.contains(node.tag)) {
                    TaggedYamlValue(node.tag.value, node.value)
                } else {
                    scalarConstructor.constructStandardScalar(node)
                }
            }

            else -> null
        }
    }

    private fun convertNodeKey(node: Node, scalarConstructor: StandardScalarConstructor): String? {
        return when (node) {
            is ScalarNode -> node.value.trim().takeIf { it.isNotEmpty() }
            else -> convertNode(node, scalarConstructor)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun unwrapTaggedScalar(value: Any?): Any? {
        val tagged = value as? TaggedYamlValue ?: return value
        return when (val wrapped = tagged.value) {
            is Map<*, *>,
            is Iterable<*>,
            -> value
            else -> wrapped
        }
    }
}
