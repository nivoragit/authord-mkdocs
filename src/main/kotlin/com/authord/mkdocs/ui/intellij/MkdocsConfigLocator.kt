package com.authord.mkdocs.ui.intellij

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

private val mkdocsConfigDiscoveryPriority = listOf(
    "mkdocs.yml",
    "mkdocs.yaml",
    "_mkdocs.yml",
    "_mkdocs.yaml",
)
private val mkdocsConfigDiscoveryPriorityByName = mkdocsConfigDiscoveryPriority
    .withIndex()
    .associate { (index, name) -> name to index }
private val mkdocsConfigDiscoveryNames = mkdocsConfigDiscoveryPriority.toSet()
private val ignoredMkdocsConfigSearchDirectories = setOf(
    ".git",
    ".idea",
    ".gradle",
    "build",
    "out",
    "node_modules",
    ".venv",
    "venv",
    "__pycache__",
)
private const val MKDOCS_CONFIG_SEARCH_MAX_DIRECTORY_DEPTH: Int = 4

internal fun findMkdocsConfig(projectRoot: Path): Path? {
    return MkdocsConfigLocator.findMkdocsConfig(projectRoot)
}

internal fun findAllMkdocsConfigs(projectRoot: Path): List<Path> {
    return MkdocsConfigLocator.findAllMkdocsConfigs(projectRoot)
}

internal fun isMkdocsConfigPath(path: String): Boolean {
    val fileName = runCatching { Path.of(path).fileName?.toString() }
        .getOrNull()
        ?.lowercase(Locale.ROOT)
        ?: return false
    return mkdocsConfigDiscoveryNames.contains(fileName)
}

internal fun mkdocsConfigCandidateFileNames(): List<String> = mkdocsConfigDiscoveryPriority

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
    private val allCacheByRootPath = ConcurrentHashMap<String, List<Path>>()

    fun findMkdocsConfig(projectRoot: Path): Path? {
        val normalizedRoot = normalizeProjectRoot(projectRoot) ?: return null
        val cacheKey = normalizedRoot.toString().replace('\\', '/')
        return cacheByRootPath.computeIfAbsent(cacheKey) {
            Optional.ofNullable(resolveConfigInRoot(normalizedRoot))
        }.orElse(null)
    }

    fun findAllMkdocsConfigs(projectRoot: Path): List<Path> {
        val normalizedRoot = normalizeProjectRoot(projectRoot) ?: return emptyList()
        val cacheKey = normalizedRoot.toString().replace('\\', '/')
        return allCacheByRootPath.computeIfAbsent(cacheKey) {
            resolveAllConfigsInRoot(normalizedRoot)
        }
    }

    fun invalidate(projectRoot: Path?) {
        val normalizedRoot = normalizeProjectRoot(projectRoot) ?: return
        val cacheKey = normalizedRoot.toString().replace('\\', '/')
        cacheByRootPath.remove(cacheKey)
        allCacheByRootPath.remove(cacheKey)
    }

    fun clear() {
        cacheByRootPath.clear()
        allCacheByRootPath.clear()
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
        resolveConfigInDirectory(projectRoot)?.let { return it }
        return resolveConfigInSubdirectories(projectRoot)
    }

    private fun resolveAllConfigsInRoot(projectRoot: Path): List<Path> {
        val discovered = mutableListOf<Path>()
        resolveConfigInDirectory(projectRoot)?.let(discovered::add)
        discovered += collectConfigsInSubdirectories(projectRoot)
        return discovered
            .mapNotNull { candidate ->
                runCatching { candidate.toAbsolutePath().normalize() }.getOrNull()
            }
            .distinctBy { it.toString().replace('\\', '/').lowercase(Locale.ROOT) }
            .sortedWith(
                compareBy<Path>(
                    { relativeDepth(projectRoot, it) },
                    { mkdocsConfigDiscoveryPriorityByName[it.fileName?.toString()?.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE },
                    { it.toString().replace('\\', '/').lowercase(Locale.ROOT) },
                ),
            )
    }

    private fun resolveConfigInDirectory(directory: Path): Path? {
        mkdocsConfigDiscoveryPriority.forEach { fileName ->
            val candidate = directory.resolve(fileName)
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun resolveConfigInSubdirectories(projectRoot: Path): Path? {
        var bestPath: Path? = null
        var bestDepth = Int.MAX_VALUE
        var bestPriority = Int.MAX_VALUE
        var bestLexicographicKey = ""

        Files.walkFileTree(
            projectRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir == projectRoot) {
                        return FileVisitResult.CONTINUE
                    }
                    val depth = relativeDepth(projectRoot, dir)
                    if (depth > MKDOCS_CONFIG_SEARCH_MAX_DIRECTORY_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    val directoryName = dir.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                    if (ignoredMkdocsConfigSearchDirectories.contains(directoryName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isRegularFile) {
                        return FileVisitResult.CONTINUE
                    }
                    val fileName = file.fileName?.toString()?.lowercase(Locale.ROOT) ?: return FileVisitResult.CONTINUE
                    val priority = mkdocsConfigDiscoveryPriorityByName[fileName] ?: return FileVisitResult.CONTINUE
                    val depth = relativeDepth(projectRoot, file)
                    if (depth > MKDOCS_CONFIG_SEARCH_MAX_DIRECTORY_DEPTH + 1) {
                        return FileVisitResult.CONTINUE
                    }

                    val normalizedFile = runCatching { file.toAbsolutePath().normalize() }.getOrNull()
                        ?: return FileVisitResult.CONTINUE
                    val lexicographicKey = normalizedFile.toString().replace('\\', '/').lowercase(Locale.ROOT)
                    val shouldReplace = when {
                        depth < bestDepth -> true
                        depth > bestDepth -> false
                        priority < bestPriority -> true
                        priority > bestPriority -> false
                        else -> bestPath == null || lexicographicKey < bestLexicographicKey
                    }
                    if (shouldReplace) {
                        bestPath = normalizedFile
                        bestDepth = depth
                        bestPriority = priority
                        bestLexicographicKey = lexicographicKey
                    }

                    return FileVisitResult.CONTINUE
                }
            },
        )

        return bestPath
    }

    private fun collectConfigsInSubdirectories(projectRoot: Path): List<Path> {
        val discovered = mutableListOf<Path>()
        Files.walkFileTree(
            projectRoot,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir == projectRoot) {
                        return FileVisitResult.CONTINUE
                    }
                    val depth = relativeDepth(projectRoot, dir)
                    if (depth > MKDOCS_CONFIG_SEARCH_MAX_DIRECTORY_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    val directoryName = dir.fileName?.toString()?.lowercase(Locale.ROOT).orEmpty()
                    if (ignoredMkdocsConfigSearchDirectories.contains(directoryName)) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isRegularFile) {
                        return FileVisitResult.CONTINUE
                    }
                    val fileName = file.fileName?.toString()?.lowercase(Locale.ROOT) ?: return FileVisitResult.CONTINUE
                    if (!mkdocsConfigDiscoveryNames.contains(fileName)) {
                        return FileVisitResult.CONTINUE
                    }
                    val depth = relativeDepth(projectRoot, file)
                    if (depth <= MKDOCS_CONFIG_SEARCH_MAX_DIRECTORY_DEPTH + 1) {
                        discovered.add(file)
                    }
                    return FileVisitResult.CONTINUE
                }
            },
        )
        return discovered
    }

    private fun relativeDepth(root: Path, candidate: Path): Int {
        return runCatching { root.relativize(candidate).nameCount }.getOrDefault(Int.MAX_VALUE)
    }
}
