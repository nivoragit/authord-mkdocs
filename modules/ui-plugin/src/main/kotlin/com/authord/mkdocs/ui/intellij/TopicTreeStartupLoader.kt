package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.core.topic.TopicTreeValidationIssue
import com.authord.mkdocs.core.topic.TopicTreeValidationService
import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.runtime.MarkdownHeadingSupport
import java.nio.file.Files
import java.nio.file.Path

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
    private val markdownContentReader: (Path) -> String = { path -> Files.readString(path) },
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
        val nodes = resolveTitlesFromMarkdownHeadings(
            nodes = resolvedNodes,
            docsDir = config.docsDir,
            docsMarkdownPaths = docsMarkdownPaths,
        )

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

    private fun resolveTitlesFromMarkdownHeadings(
        nodes: List<TopicNavNode>,
        docsDir: String,
        docsMarkdownPaths: List<String>,
    ): List<TopicNavNode> {
        if (nodes.isEmpty() || docsMarkdownPaths.isEmpty()) {
            return nodes
        }

        val headingByPath = mutableMapOf<String, String>()
        docsMarkdownPaths.forEach { absolutePathString ->
            val relativePath = toRelativePath(docsDir, absolutePathString) ?: return@forEach
            val absolutePath = runCatching { Path.of(absolutePathString).toAbsolutePath().normalize() }.getOrNull()
                ?: return@forEach
            if (!Files.isRegularFile(absolutePath)) {
                return@forEach
            }
            val heading = runCatching {
                MarkdownHeadingSupport.extractFirstH1(markdownContentReader(absolutePath))
            }.getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@forEach
            headingByPath[pathPolicy.comparisonKey(pathPolicy.normalize(relativePath).trimStart('/'))] = heading
        }

        if (headingByPath.isEmpty()) {
            return nodes
        }

        fun resolveNode(node: TopicNavNode): TopicNavNode {
            val resolvedChildren = node.children.map(::resolveNode)
            val key = node.path
                ?.let(pathPolicy::normalize)
                ?.trimStart('/')
                ?.let(pathPolicy::comparisonKey)
            val resolvedTitle = key?.let(headingByPath::get) ?: node.title
            return node.copy(
                title = resolvedTitle,
                children = resolvedChildren,
            )
        }

        return nodes.map(::resolveNode)
    }

    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs()
}
