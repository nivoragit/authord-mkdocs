package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal data class SiteContext(
    val projectRoot: Path,
    val configPath: Path,
    val docsDirPath: Path,
    val useDirectoryUrls: Boolean,
)

/**
 * Resolves the active MkDocs site context used by startup and routing.
 */
internal class SiteContextResolver(
    private val configLocator: (Path) -> Path? = ::findMkdocsConfig,
) {
    fun resolveProjectDefault(projectRoot: Path): SiteContext? {
        val normalizedRoot = normalizeDirectory(projectRoot) ?: return null
        val discoveredConfig = configLocator(normalizedRoot) ?: return null
        return resolveFromConfig(
            projectRoot = normalizedRoot,
            configPath = discoveredConfig,
            visited = emptySet(),
        )
    }

    fun resolveFromRuntimeCommand(projectRoot: Path?, command: List<String>): SiteContext? {
        val normalizedRoot = projectRoot?.let(::normalizeDirectory)
        val runtimeConfig = resolveRuntimeConfigPath(command, normalizedRoot)
        if (runtimeConfig != null) {
            val inferredRoot = normalizedRoot ?: runtimeConfig.parent?.let(::normalizeDirectory)
            if (inferredRoot != null) {
                resolveFromConfig(
                    projectRoot = inferredRoot,
                    configPath = runtimeConfig,
                    visited = emptySet(),
                )?.let { return it }
            }
        }

        return normalizedRoot?.let(::resolveProjectDefault)
    }

    private fun resolveFromConfig(
        projectRoot: Path,
        configPath: Path,
        visited: Set<Path>,
    ): SiteContext? {
        val normalizedConfig = normalizeFile(configPath) ?: return null
        if (!Files.exists(normalizedConfig) || !Files.isRegularFile(normalizedConfig)) {
            return null
        }
        if (normalizedConfig in visited) {
            return null
        }

        val lines = runCatching { Files.readAllLines(normalizedConfig) }.getOrNull() ?: return null
        val docsDirRaw = findTopLevelScalar(lines, "docs_dir")
        val useDirectoryUrlsRaw = findTopLevelScalar(lines, "use_directory_urls")
        val inheritRaw = findTopLevelScalar(lines, "INHERIT")

        val inheritedContext = inheritRaw
            ?.let { inherited -> resolveInheritedConfigPath(baseConfigPath = normalizedConfig, rawValue = inherited) }
            ?.let { inheritedPath ->
                resolveFromConfig(
                    projectRoot = projectRoot,
                    configPath = inheritedPath,
                    visited = visited + normalizedConfig,
                )
            }

        val effectiveConfigPath = inheritedContext?.configPath ?: normalizedConfig
        val docsDirPath = when {
            docsDirRaw != null -> resolveDocsDirPath(baseConfigPath = normalizedConfig, rawDocsDirValue = docsDirRaw)
            inheritedContext != null -> inheritedContext.docsDirPath
            else -> resolveImplicitDocsDirPath(normalizedConfig)
        }
        val useDirectoryUrls = parseYamlBoolean(useDirectoryUrlsRaw)
            ?: inheritedContext?.useDirectoryUrls
            ?: true

        return SiteContext(
            projectRoot = projectRoot,
            configPath = effectiveConfigPath,
            docsDirPath = docsDirPath,
            useDirectoryUrls = useDirectoryUrls,
        )
    }

    private fun resolveRuntimeConfigPath(command: List<String>, projectRoot: Path?): Path? {
        val flagIndex = command.indexOf("-f")
        val candidate = command.getOrNull(flagIndex + 1)?.trim().orEmpty()
        if (candidate.isBlank()) {
            return null
        }

        val configPath = runCatching { Path.of(candidate) }.getOrNull() ?: return null
        val absolute = when {
            configPath.isAbsolute -> configPath
            projectRoot != null -> projectRoot.resolve(configPath)
            else -> configPath.toAbsolutePath()
        }
        return normalizeFile(absolute)
    }

    private fun resolveInheritedConfigPath(baseConfigPath: Path, rawValue: String): Path? {
        val scalar = parseYamlScalar(rawValue)
        if (scalar.isBlank()) {
            return null
        }

        val candidate = runCatching { Path.of(scalar) }.getOrNull() ?: return null
        val absolute = if (candidate.isAbsolute) {
            candidate
        } else {
            baseConfigPath.parent.resolve(candidate)
        }
        return normalizeFile(absolute)
    }

    private fun resolveDocsDirPath(baseConfigPath: Path, rawDocsDirValue: String): Path {
        val docsDirValue = parseYamlScalar(rawDocsDirValue)
        if (docsDirValue.isBlank()) {
            return resolveImplicitDocsDirPath(baseConfigPath)
        }

        val docsDirPath = runCatching { Path.of(docsDirValue) }.getOrNull()
            ?: return resolveImplicitDocsDirPath(baseConfigPath)
        val absolute = if (docsDirPath.isAbsolute) {
            docsDirPath
        } else {
            baseConfigPath.parent.resolve(docsDirPath)
        }
        return absolute.toAbsolutePath().normalize()
    }

    private fun findTopLevelScalar(lines: List<String>, key: String): String? {
        val escaped = Regex.escape(key)
        val regex = Regex("""^\s*(?:["']?$escaped["']?)\s*:\s*(.+?)\s*$""")
        return lines.asSequence()
            .map { line -> line.substringBefore('#').trimEnd() }
            .filter { it.isNotBlank() }
            .mapNotNull { line -> regex.find(line)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun parseYamlScalar(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'')) {
            return trimmed.substring(1, trimmed.length - 1).replace("''", "'")
        }
        if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return trimmed
    }

    private fun parseYamlBoolean(rawValue: String?): Boolean? {
        val normalized = rawValue
            ?.let(::parseYamlScalar)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null
        return when (normalized) {
            "true", "yes", "on", "1" -> true
            "false", "no", "off", "0" -> false
            else -> null
        }
    }

    private fun normalizeDirectory(path: Path): Path? {
        val normalized = runCatching { path.toAbsolutePath().normalize() }.getOrNull() ?: return null
        if (!Files.exists(normalized) || !Files.isDirectory(normalized)) {
            return null
        }
        return normalized
    }

    private fun normalizeFile(path: Path): Path? {
        return runCatching { path.toAbsolutePath().normalize() }.getOrNull()
    }
}
