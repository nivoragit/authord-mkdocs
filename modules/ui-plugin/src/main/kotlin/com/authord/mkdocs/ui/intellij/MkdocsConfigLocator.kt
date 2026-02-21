package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

internal fun findMkdocsConfig(projectRoot: Path): Path? {
    return MkdocsConfigLocator.findMkdocsConfig(projectRoot)
}

internal fun isMarkdownPath(path: String): Boolean {
    val normalized = path.lowercase()
    return normalized.endsWith(".md") || normalized.endsWith(".markdown")
}

internal fun hasMkdocsConfig(projectBasePath: String): Boolean {
    val base = runCatching { Path.of(projectBasePath).toAbsolutePath().normalize() }.getOrNull() ?: return false
    return findMkdocsConfig(base) != null
}

internal fun hasConfigFile(projectBasePath: String): Boolean {
    return hasMkdocsConfig(projectBasePath)
}

internal fun isUnderProject(projectBasePath: String, selectedPath: String): Boolean {
    val projectPath = runCatching { Path.of(projectBasePath).toAbsolutePath().normalize() }.getOrNull() ?: return false
    val candidatePath = runCatching { Path.of(selectedPath).toAbsolutePath().normalize() }.getOrNull() ?: return false
    return candidatePath.startsWith(projectPath)
}

internal fun invalidateMkdocsConfigCache(projectRoot: Path?) {
    MkdocsConfigLocator.invalidate(projectRoot)
}

internal fun clearMkdocsConfigCacheForTests() {
    MkdocsConfigLocator.clear()
}

private object MkdocsConfigLocator {
    private val cacheByRootPath = ConcurrentHashMap<String, Optional<Path>>()

    fun findMkdocsConfig(projectRoot: Path): Path? {
        val normalizedRoot = normalizeProjectRoot(projectRoot) ?: return null
        val cacheKey = normalizedRoot.toString().replace('\\', '/')
        return cacheByRootPath.computeIfAbsent(cacheKey) {
            Optional.ofNullable(resolveConfigInRoot(normalizedRoot))
        }.orElse(null)
    }

    fun invalidate(projectRoot: Path?) {
        val normalizedRoot = normalizeProjectRoot(projectRoot) ?: return
        val cacheKey = normalizedRoot.toString().replace('\\', '/')
        cacheByRootPath.remove(cacheKey)
    }

    fun clear() {
        cacheByRootPath.clear()
    }

    private fun normalizeProjectRoot(projectRoot: Path?): Path? {
        val normalizedRoot = runCatching {
            projectRoot?.toAbsolutePath()?.normalize()
        }.getOrNull() ?: return null
        if (!Files.exists(normalizedRoot) || !Files.isDirectory(normalizedRoot)) {
            return null
        }
        return normalizedRoot
    }

    private fun resolveConfigInRoot(projectRoot: Path): Path? {
        val yml = projectRoot.resolve("mkdocs.yml")
        if (Files.exists(yml) && Files.isRegularFile(yml)) {
            return yml
        }

        val yaml = projectRoot.resolve("mkdocs.yaml")
        if (Files.exists(yaml) && Files.isRegularFile(yaml)) {
            return yaml
        }

        return null
    }
}
