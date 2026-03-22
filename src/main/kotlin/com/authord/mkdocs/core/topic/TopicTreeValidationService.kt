package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.TopicNavNode
import java.net.URI

/**
 * Validation categories for startup reconciliation and mutation feedback.
 */
enum class TopicTreeValidationIssueType {
    BROKEN_PATH,
    DUPLICATE_REFERENCE,
    MALFORMED_LINK,
}

/**
 * Structured validation issue raised for topic tree/nav data.
 */
data class TopicTreeValidationIssue(
    val type: TopicTreeValidationIssueType,
    val reference: String,
    val detail: String,
)

/**
 * Validates nav nodes against docs paths for broken/duplicate/malformed entries.
 */
class TopicTreeValidationService(
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
) {
    /**
     * Validates nav entries and returns deterministic issue ordering.
     */
    fun validate(
        navNodes: List<TopicNavNode>,
        docsRelativePaths: List<String>,
    ): List<TopicTreeValidationIssue> {
        val issues = mutableListOf<TopicTreeValidationIssue>()
        val flattened = flatten(navNodes)
        val docsKeys = docsRelativePaths
            .map { pathPolicy.comparisonKey(pathPolicy.normalize(it).trim('/')) }
            .toSet()

        val pathKeyCounts = linkedMapOf<String, MutableList<String>>()

        flattened.forEach { node ->
            node.path?.let { rawPath ->
                val normalizedPath = pathPolicy.normalize(rawPath).trim('/')
                val pathKey = pathPolicy.comparisonKey(normalizedPath)
                pathKeyCounts.getOrPut(pathKey) { mutableListOf() } += normalizedPath
                if (pathKey !in docsKeys) {
                    issues += TopicTreeValidationIssue(
                        type = TopicTreeValidationIssueType.BROKEN_PATH,
                        reference = normalizedPath,
                        detail = "Nav entry points to missing markdown file",
                    )
                }
            }

            node.externalUrl?.let { url ->
                if (!isValidExternalUrl(url)) {
                    issues += TopicTreeValidationIssue(
                        type = TopicTreeValidationIssueType.MALFORMED_LINK,
                        reference = url,
                        detail = "External link must be an absolute http(s) URL",
                    )
                }
            }
        }

        pathKeyCounts
            .values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                val reference = duplicates.first()
                issues += TopicTreeValidationIssue(
                    type = TopicTreeValidationIssueType.DUPLICATE_REFERENCE,
                    reference = reference,
                    detail = "Same markdown file is referenced multiple times in nav",
                )
            }

        return issues
            .distinctBy { "${it.type}:${pathPolicy.comparisonKey(it.reference)}" }
            .sortedWith(
                compareBy<TopicTreeValidationIssue> { it.type.ordinal }
                    .thenBy { pathPolicy.comparisonKey(it.reference) },
            )
    }

    private fun flatten(nodes: List<TopicNavNode>): List<TopicNavNode> {
        val flattened = mutableListOf<TopicNavNode>()
        fun visit(node: TopicNavNode) {
            flattened += node
            node.children.forEach(::visit)
        }
        nodes.forEach(::visit)
        return flattened
    }

    private fun isValidExternalUrl(url: String): Boolean {
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank() && !url.contains(' ')
        }.getOrDefault(false)
    }
}
