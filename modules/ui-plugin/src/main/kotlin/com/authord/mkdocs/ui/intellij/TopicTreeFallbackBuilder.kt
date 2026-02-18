package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.ports.topic.TopicNavNode

/**
 * Builds a deterministic topic tree from docs_dir markdown files when nav is absent.
 */
class TopicTreeFallbackBuilder(
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
) {
    /**
     * Produces deterministic nav nodes from docs_dir markdown paths.
     */
    fun build(docsDir: String, docsMarkdownPaths: List<String>): List<TopicNavNode> {
        val normalizedDocsDir = pathPolicy.normalize(docsDir).trimEnd('/')
        val relativeMarkdownPaths = docsMarkdownPaths
            .mapNotNull { toRelativePath(normalizedDocsDir, it) }
            .distinctBy(pathPolicy::comparisonKey)
            .sortedBy(pathPolicy::comparisonKey)

        val sectionByPath = linkedMapOf<String, MutableSection>()
        val rootItems = mutableListOf<MutableItem>()

        relativeMarkdownPaths.forEach { relativePath ->
            val segments = relativePath.split('/')
            val fileName = segments.last()
            val directories = segments.dropLast(1)

            var parentSectionPath: String? = null
            directories.forEach { directory ->
                val sectionPath = if (parentSectionPath == null) directory else "$parentSectionPath/$directory"
                val section = sectionByPath.getOrPut(sectionPath) {
                    MutableSection(
                        path = sectionPath,
                        title = titleFromSegment(directory),
                    )
                }
                if (parentSectionPath == null) {
                    if (rootItems.none { it is MutableSection && pathPolicy.equivalent(it.path, section.path) }) {
                        rootItems += section
                    }
                } else {
                    val parentPath = requireNotNull(parentSectionPath)
                    val parent = sectionByPath.getValue(parentPath)
                    if (parent.children.none { it is MutableSection && pathPolicy.equivalent(it.path, section.path) }) {
                        parent.children += section
                    }
                }
                parentSectionPath = sectionPath
            }

            val pageNode = MutablePage(
                path = relativePath,
                title = titleFromSegment(fileName.removeSuffix(".md")),
            )
            if (parentSectionPath == null) {
                rootItems += pageNode
            } else {
                val parentPath = requireNotNull(parentSectionPath)
                sectionByPath.getValue(parentPath).children += pageNode
            }
        }

        return rootItems
            .sortedBy { pathPolicy.comparisonKey(it.path) }
            .map { it.toNavNode(pathPolicy) }
    }

    private fun toRelativePath(normalizedDocsDir: String, docsMarkdownPath: String): String? {
        val normalizedPath = pathPolicy.normalize(docsMarkdownPath)
        if (!normalizedPath.endsWith(".md")) {
            return null
        }

        val prefix = "$normalizedDocsDir/"
        return when {
            normalizedPath.startsWith(prefix) -> normalizedPath.removePrefix(prefix)
            normalizedPath == normalizedDocsDir -> null
            normalizedPath.contains('/') -> null
            else -> normalizedPath
        }
    }

    private fun titleFromSegment(raw: String): String {
        return raw
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase() else first.toString()
                }
            }
            .ifBlank { raw }
    }
}

private sealed interface MutableItem {
    val path: String
    fun toNavNode(pathPolicy: PathPolicy): TopicNavNode
}

private data class MutablePage(
    override val path: String,
    private val title: String,
) : MutableItem {
    override fun toNavNode(pathPolicy: PathPolicy): TopicNavNode {
        return TopicNavNode(
            nodeId = "page:$path",
            title = title,
            path = path,
        )
    }
}

private data class MutableSection(
    override val path: String,
    private val title: String,
    val children: MutableList<MutableItem> = mutableListOf(),
) : MutableItem {
    override fun toNavNode(pathPolicy: PathPolicy): TopicNavNode {
        return TopicNavNode(
            nodeId = "section:$path",
            title = title,
            children = children
                .sortedBy { pathPolicy.comparisonKey(it.path) }
                .map { it.toNavNode(pathPolicy) },
        )
    }
}
