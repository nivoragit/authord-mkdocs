package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.DocsFileGateway
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Filesystem-backed implementation of [DocsFileGateway] scoped to an instance docs_dir.
 */
class DocsFileGatewayAdapter(
    private val markdownLinkRewriter: MarkdownLinkRewriter = MarkdownLinkRewriter(),
    private val trashMover: (Path) -> Boolean = ::moveToSystemTrash,
) : DocsFileGateway {
    override fun createMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        initialContent: String,
    ): TopicGatewayResult<String> {
        val pathResult = resolveMarkdownPath(instance, relativePath)
        val absolutePath = when (pathResult) {
            is TopicGatewayResult.Success -> pathResult.value
            is TopicGatewayResult.Failure -> return pathResult
        }

        return runCatching {
            Files.createDirectories(absolutePath.parent)
            Files.writeString(absolutePath, initialContent)
            toRelative(instance, absolutePath)
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "Create markdown failed: ${it.message}"),
                )
            },
        )
    }

    override fun deleteMarkdownFile(
        instance: TopicInstanceRef,
        relativePath: String,
        mode: TopicDeleteMode,
    ): TopicGatewayResult<String> {
        val pathResult = resolveMarkdownPath(instance, relativePath)
        val absolutePath = when (pathResult) {
            is TopicGatewayResult.Success -> pathResult.value
            is TopicGatewayResult.Failure -> return pathResult
        }

        if (mode == TopicDeleteMode.NAV_ONLY) {
            return TopicGatewayResult.Success(toRelative(instance, absolutePath))
        }

        if (!Files.exists(absolutePath)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "File does not exist: ${absolutePath.fileName}"),
            )
        }

        return runCatching {
            if (trashMover(absolutePath)) {
                toRelative(instance, absolutePath)
            } else {
                moveToRecovery(instance, absolutePath)
            }
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "Delete markdown failed: ${it.message}"),
                )
            },
        )
    }

    override fun renameMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return moveInternal(instance, fromRelativePath, toRelativePath)
    }

    override fun moveMarkdownFile(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        return moveInternal(instance, fromRelativePath, toRelativePath)
    }

    override fun rewriteRelativeMarkdownLinks(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<Int> {
        return markdownLinkRewriter.rewrite(instance, fromRelativePath, toRelativePath)
    }

    override fun upsertMarkdownTitleHeading(
        instance: TopicInstanceRef,
        relativePath: String,
        title: String,
    ): TopicGatewayResult<String> {
        val pathResult = resolveMarkdownPath(instance, relativePath)
        val absolutePath = when (pathResult) {
            is TopicGatewayResult.Success -> pathResult.value
            is TopicGatewayResult.Failure -> return pathResult
        }

        if (!Files.exists(absolutePath)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "File does not exist: ${absolutePath.fileName}"),
            )
        }

        return runCatching {
            val current = Files.readString(absolutePath)
            val updated = MarkdownHeadingSupport.upsertFirstH1(current, title)
            Files.writeString(absolutePath, updated)
            toRelative(instance, absolutePath)
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "Update markdown heading failed: ${it.message}"),
                )
            },
        )
    }

    private fun moveInternal(
        instance: TopicInstanceRef,
        fromRelativePath: String,
        toRelativePath: String,
    ): TopicGatewayResult<String> {
        val sourceResult = resolveMarkdownPath(instance, fromRelativePath)
        val targetResult = resolveMarkdownPath(instance, toRelativePath)
        val source = when (sourceResult) {
            is TopicGatewayResult.Success -> sourceResult.value
            is TopicGatewayResult.Failure -> return sourceResult
        }
        val target = when (targetResult) {
            is TopicGatewayResult.Success -> targetResult.value
            is TopicGatewayResult.Failure -> return targetResult
        }

        return runCatching {
            Files.createDirectories(target.parent)
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
            toRelative(instance, target)
        }.fold(
            onSuccess = { TopicGatewayResult.Success(it) },
            onFailure = {
                TopicGatewayResult.Failure(
                    DefaultTopicSyncError(TopicSyncErrorCode.FILE_IO, "Move markdown failed: ${it.message}"),
                )
            },
        )
    }

    private fun resolveMarkdownPath(instance: TopicInstanceRef, relativePath: String): TopicGatewayResult<Path> {
        val normalized = normalizeRelative(relativePath)
        if (!normalized.endsWith(".md")) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Markdown path must end with .md"),
            )
        }

        val docsDir = docsDir(instance)
        val resolved = docsDir.resolve(normalized).normalize()
        if (!resolved.startsWith(docsDir)) {
            return TopicGatewayResult.Failure(
                DefaultTopicSyncError(TopicSyncErrorCode.INSTANCE_SCOPE, "Path escaped docs_dir scope"),
            )
        }

        return TopicGatewayResult.Success(resolved)
    }

    private fun docsDir(instance: TopicInstanceRef): Path = Paths.get(instance.docsDirPath).normalize()

    private fun moveToRecovery(instance: TopicInstanceRef, absolutePath: Path): String {
        val docsDir = docsDir(instance)
        val recoveryDir = docsDir.resolve(".recovery")
        Files.createDirectories(recoveryDir)
        val recoveryPath = recoveryDir.resolve("${absolutePath.fileName}-${System.currentTimeMillis()}")
        Files.move(absolutePath, recoveryPath, StandardCopyOption.REPLACE_EXISTING)
        return toRelative(instance, recoveryPath)
    }

    private fun normalizeRelative(path: String): String {
        return path.replace('\\', '/').trim().trimStart('/')
    }

    private fun toRelative(instance: TopicInstanceRef, path: Path): String {
        return docsDir(instance).relativize(path).toString().replace('\\', '/')
    }

    private companion object {
        private fun moveToSystemTrash(path: Path): Boolean {
            val desktop = runCatching { Desktop.getDesktop() }.getOrNull()
            return desktop
                ?.takeIf { Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.MOVE_TO_TRASH) }
                ?.let { systemDesktop -> runCatching { systemDesktop.moveToTrash(path.toFile()) }.getOrDefault(false) } == true
        }
    }
}
