package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.ports.topic.TopicNavNode

/**
 * Derives markdown files in docs_dir that are not referenced by nav.
 */
class UnlinkedFilesBucketService(
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
) {
    /**
     * Returns deterministic unlinked markdown paths under docs_dir.
     */
    fun derive(
        docsDir: String,
        docsMarkdownPaths: List<String>,
        navNodes: List<TopicNavNode>,
    ): List<String> {
        val normalizedDocsDir = pathPolicy.normalize(docsDir).trimEnd('/')
        val navReferencedPaths = flattenNavPaths(navNodes)
            .map { pathPolicy.comparisonKey(pathPolicy.normalize(it).trim('/')) }
            .toSet()

        return docsMarkdownPaths
            .mapNotNull { docsPath ->
                val normalized = pathPolicy.normalize(docsPath)
                if (!normalized.endsWith(".md")) {
                    return@mapNotNull null
                }

                val relative = toRelativePath(normalizedDocsDir, normalized) ?: return@mapNotNull null
                val key = pathPolicy.comparisonKey(relative)
                if (key in navReferencedPaths) {
                    return@mapNotNull null
                }
                if (normalized.startsWith("/")) normalized else "$normalizedDocsDir/$relative"
            }
            .distinctBy(pathPolicy::comparisonKey)
            .sortedBy(pathPolicy::comparisonKey)
    }

    private fun toRelativePath(docsDir: String, normalizedPath: String): String? {
        val prefixed = "$docsDir/"
        return when {
            normalizedPath.startsWith(prefixed) -> normalizedPath.removePrefix(prefixed).trim('/')
            normalizedPath == docsDir -> null
            normalizedPath.contains('/') -> null
            else -> normalizedPath.trim('/')
        }
    }

    private fun flattenNavPaths(nodes: List<TopicNavNode>): List<String> {
        val paths = mutableListOf<String>()
        fun visit(node: TopicNavNode) {
            node.path?.let { paths += it }
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return paths
    }
}
