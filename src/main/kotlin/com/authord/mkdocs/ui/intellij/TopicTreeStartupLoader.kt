package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.core.topic.TopicTreeValidationIssue
import com.authord.mkdocs.core.topic.TopicTreeValidationService
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode

/**
 * Source mode used to construct startup topic-tree state.
 */
enum class StartupTreeSource {
    NAV,
    FALLBACK,
}

/**
 * Startup tree state produced from canonical nav or deterministic fallback.
 */
data class StartupTreeState(
    val source: StartupTreeSource,
    val nodes: List<TopicNavNode>,
    val navOrderedPaths: List<String>,
    val unlinkedPaths: List<String>,
    val validationIssues: List<TopicTreeValidationIssue>,
    val destructiveChangesApplied: Boolean,
    val instanceId: String = "default",
)

/**
 * Builds startup topic-tree state from canonical nav when available.
 */
class TopicTreeStartupLoader(
    private val fallbackBuilder: TopicTreeFallbackBuilder = TopicTreeFallbackBuilder(),
    private val unlinkedFilesBucketService: UnlinkedFilesBucketService = UnlinkedFilesBucketService(),
    private val validationService: TopicTreeValidationService = TopicTreeValidationService(PathPolicy.forCurrentOs()),
) {
    /**
     * Loads startup tree state from config and docs markdown paths.
     */
    fun load(config: MkDocsConfigDocument, docsMarkdownPaths: List<String>): StartupTreeState {
        val source = if (config.navPresent) {
            StartupTreeSource.NAV
        } else {
            StartupTreeSource.FALLBACK
        }

        val resolvedNodes = if (source == StartupTreeSource.NAV) {
            config.nav
        } else {
            fallbackBuilder.build(config.docsDir, docsMarkdownPaths)
        }
        val nodes = resolvedNodes

        val navOrderedPaths = flattenPaths(nodes)
        val docsRelativePaths = docsMarkdownPaths.mapNotNull { toRelativePath(config.docsDir, it) }
        val validationIssues = if (source == StartupTreeSource.NAV) {
            validationService.validate(config.nav, docsRelativePaths)
        } else {
            emptyList()
        }

        return StartupTreeState(
            source = source,
            nodes = nodes,
            navOrderedPaths = navOrderedPaths,
            unlinkedPaths = unlinkedFilesBucketService.derive(
                docsDir = config.docsDir,
                docsMarkdownPaths = docsMarkdownPaths,
                navNodes = nodes,
            ),
            validationIssues = validationIssues,
            destructiveChangesApplied = false,
        )
    }

    private fun flattenPaths(nodes: List<TopicNavNode>): List<String> {
        val ordered = mutableListOf<String>()
        fun visit(node: TopicNavNode) {
            node.path?.let { ordered += it }
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return ordered
    }

    private fun toRelativePath(docsDir: String, docsPath: String): String? {
        val normalizedDocsDir = pathPolicy.normalize(docsDir).trimEnd('/')
        val normalizedPath = pathPolicy.normalize(docsPath)
        val prefix = "$normalizedDocsDir/"
        return when {
            normalizedPath.startsWith(prefix) -> normalizedPath.removePrefix(prefix)
            normalizedPath.endsWith(".md") && !normalizedPath.contains('/') -> normalizedPath
            else -> null
        }
    }

    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs()
}
