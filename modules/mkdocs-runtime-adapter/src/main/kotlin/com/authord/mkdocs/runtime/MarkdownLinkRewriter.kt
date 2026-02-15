package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Rewrites relative markdown links within docs scope for rename/move operations.
 */
class MarkdownLinkRewriter {
    private val markdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")

    /**
     * Rewrites relative markdown references pointing to [fromRelativePath] into [toRelativePath].
     */
    fun rewrite(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        val docsDir = Paths.get(instance.docsDirPath).normalize()
        val source = docsDir.resolve(normalizePath(fromRelativePath)).normalize()
        val target = docsDir.resolve(normalizePath(toRelativePath)).normalize()

        if (!source.startsWith(docsDir) || !target.startsWith(docsDir)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Rewrite path escaped docs_dir scope"),
            )
        }

        return runCatching {
            var replacements = 0
            Files.walk(docsDir).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                    .forEach { markdownFile ->
                        val content = Files.readString(markdownFile)
                        val rewritten = rewriteFileContent(markdownFile, content, source, target)
                        if (rewritten.first != content) {
                            Files.writeString(markdownFile, rewritten.first)
                        }
                        replacements += rewritten.second
                    }
            }
            replacements
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "Markdown link rewrite failed: ${it.message}"),
                )
            },
        )
    }

    private fun rewriteFileContent(file: Path, content: String, source: Path, target: Path): Pair<String, Int> {
        val result = StringBuilder()
        var cursor = 0
        var replacements = 0

        markdownLinkRegex.findAll(content).forEach { match ->
            result.append(content, cursor, match.range.first)

            val label = match.groupValues[1]
            val rawTarget = match.groupValues[2]
            val rewrittenTarget = rewriteOneTarget(file, rawTarget, source, target)
            if (rewrittenTarget != rawTarget) {
                replacements += 1
            }

            result.append("[").append(label).append("](").append(rewrittenTarget).append(")")
            cursor = match.range.last + 1
        }

        result.append(content.substring(cursor))
        return result.toString() to replacements
    }

    private fun rewriteOneTarget(file: Path, rawTarget: String, source: Path, target: Path): String {
        if (isSkippableTarget(rawTarget)) {
            return rawTarget
        }

        val targetPath = rawTarget.substringBefore('#')
        val fragmentSuffix = rawTarget.removePrefix(targetPath)
        if (!targetPath.endsWith(".md")) {
            return rawTarget
        }

        val absoluteLinkTarget = file.parent.resolve(normalizePath(targetPath)).normalize()
        if (absoluteLinkTarget != source) {
            return rawTarget
        }

        val rewritten = normalizePath(file.parent.relativize(target).toString())
        return rewritten + fragmentSuffix
    }

    private fun isSkippableTarget(target: String): Boolean {
        val normalized = target.trim()
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("mailto:") ||
            normalized.startsWith("#") ||
            normalized.startsWith("/")
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim()
    }
}
