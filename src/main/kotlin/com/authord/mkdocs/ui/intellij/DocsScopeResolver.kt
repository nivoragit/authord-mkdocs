package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal enum class DocsScopeStatus {
    DOCS_SCOPED,
    NON_DOCS,
    AMBIGUOUS,
    MISCONFIGURED,
}

internal data class DocsScopeResult(
    val status: DocsScopeStatus,
    val configPath: String? = null,
    val docsDirPath: String? = null,
    val detail: String? = null,
)

internal class DocsScopeResolver(
    private val activeConfigPathResolver: (Project) -> String? = { project ->
        runCatching { project.getService(PluginRuntimeIntegrationService::class.java) }
            .getOrNull()
            ?.activeRuntimeConfigPath()
    },
) {
    fun resolve(project: Project, filePath: String): DocsScopeResult {
        if (!isMarkdownPath(filePath)) {
            return DocsScopeResult(status = DocsScopeStatus.NON_DOCS, detail = "Not a markdown file")
        }

        val projectRoot = normalizeProjectRoot(project.basePath)
            ?: return DocsScopeResult(status = DocsScopeStatus.MISCONFIGURED, detail = "Project base path unavailable")
        val selectedFile = normalizeSelectedPath(projectRoot, filePath)
            ?: return DocsScopeResult(status = DocsScopeStatus.NON_DOCS, detail = "Selected file path is invalid")
        if (!selectedFile.startsWith(projectRoot)) {
            return DocsScopeResult(status = DocsScopeStatus.NON_DOCS, detail = "Selected file is outside project root")
        }

        val allConfigs = findAllMkdocsConfigs(projectRoot)
            .mapNotNull { candidate -> runCatching { candidate.toAbsolutePath().normalize() }.getOrNull() }
        if (allConfigs.isEmpty()) {
            return DocsScopeResult(status = DocsScopeStatus.NON_DOCS, detail = "No MkDocs config discovered")
        }

        val activeConfig = activeConfigPathResolver(project)
            ?.let { raw -> runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrNull() }

        val scopes = orderedScopes(activeConfig, allConfigs).map { configPath ->
            resolveConfigScope(projectRoot, configPath)
        }

        val activeScope = scopes.firstOrNull {
            activeConfig != null && it.configPath == activeConfig
        }
        if (activeScope != null && activeScope.isValid && selectedFile.startsWith(activeScope.docsDirPath!!)) {
            return DocsScopeResult(
                status = DocsScopeStatus.DOCS_SCOPED,
                configPath = normalizePath(activeScope.configPath),
                docsDirPath = normalizePath(activeScope.docsDirPath),
                detail = "Matched active runtime configuration",
            )
        }

        val matches = scopes.filter { scope ->
            scope.isValid && selectedFile.startsWith(scope.docsDirPath!!)
        }
        if (matches.size == 1) {
            val match = matches.single()
            return DocsScopeResult(
                status = DocsScopeStatus.DOCS_SCOPED,
                configPath = normalizePath(match.configPath),
                docsDirPath = normalizePath(match.docsDirPath!!),
                detail = "Matched a unique docs scope",
            )
        }

        if (matches.size > 1) {
            val longestPrefix = matches.maxOf { normalizePath(it.docsDirPath!!).length }
            val nearest = matches.filter { normalizePath(it.docsDirPath!!).length == longestPrefix }
            if (nearest.size == 1) {
                val match = nearest.single()
                return DocsScopeResult(
                    status = DocsScopeStatus.DOCS_SCOPED,
                    configPath = normalizePath(match.configPath),
                    docsDirPath = normalizePath(match.docsDirPath!!),
                    detail = "Matched nearest docs scope",
                )
            }

            return DocsScopeResult(
                status = DocsScopeStatus.AMBIGUOUS,
                detail = "Multiple MkDocs docs scopes match this markdown path",
            )
        }

        val misconfigured = scopes.firstOrNull { !it.isValid }
        if (misconfigured != null) {
            return DocsScopeResult(
                status = DocsScopeStatus.MISCONFIGURED,
                configPath = normalizePath(misconfigured.configPath),
                detail = misconfigured.invalidReason ?: "MkDocs docs_dir is invalid",
            )
        }

        return DocsScopeResult(
            status = DocsScopeStatus.NON_DOCS,
            detail = "Markdown is outside all resolved docs scopes",
        )
    }

    private fun orderedScopes(activeConfig: Path?, allConfigs: List<Path>): List<Path> {
        if (activeConfig == null) {
            return allConfigs
        }

        val normalizedActive = runCatching { activeConfig.toAbsolutePath().normalize() }.getOrNull() ?: return allConfigs
        val resolved = allConfigs.toMutableList()
        val existingIndex = resolved.indexOfFirst { it == normalizedActive }
        if (existingIndex >= 0) {
            val existing = resolved.removeAt(existingIndex)
            resolved.add(0, existing)
            return resolved
        }

        resolved.add(0, normalizedActive)
        return resolved
    }

    private fun resolveConfigScope(projectRoot: Path, configPath: Path): ConfigScope {
        if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
            return ConfigScope(
                configPath = configPath,
                docsDirPath = null,
                invalidReason = "MkDocs config file does not exist",
            )
        }

        val docsDirRaw = readDocsDirValue(configPath)
        val normalizedDocsDir = if (docsDirRaw == null) {
            resolveImplicitDocsDirPath(configPath)
        } else {
            val docsDirCandidate = runCatching { Path.of(docsDirRaw) }.getOrNull()
                ?: return ConfigScope(
                    configPath = configPath,
                    docsDirPath = null,
                    invalidReason = "Unable to parse docs_dir value",
                )
            val docsDirPath = if (docsDirCandidate.isAbsolute) docsDirCandidate else configPath.parent.resolve(docsDirCandidate)
            runCatching { docsDirPath.toAbsolutePath().normalize() }.getOrNull()
                ?: return ConfigScope(
                    configPath = configPath,
                    docsDirPath = null,
                    invalidReason = "Unable to normalize docs_dir path",
                )
        }

        if (!normalizedDocsDir.startsWith(projectRoot)) {
            return ConfigScope(
                configPath = configPath,
                docsDirPath = null,
                invalidReason = "docs_dir resolves outside project root",
            )
        }

        if (!Files.exists(normalizedDocsDir) || !Files.isDirectory(normalizedDocsDir)) {
            return ConfigScope(
                configPath = configPath,
                docsDirPath = null,
                invalidReason = "docs_dir does not exist: ${normalizePath(normalizedDocsDir)}",
            )
        }

        return ConfigScope(
            configPath = configPath,
            docsDirPath = normalizedDocsDir,
            invalidReason = null,
        )
    }

    private fun normalizeProjectRoot(basePath: String?): Path? {
        val path = basePath ?: return null
        return runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull()
            ?.takeIf { Files.exists(it) && Files.isDirectory(it) }
    }

    private fun normalizeSelectedPath(projectRoot: Path, selectedPath: String): Path? {
        val candidate = runCatching { Path.of(selectedPath) }.getOrNull() ?: return null
        val absolute = if (candidate.isAbsolute) candidate else projectRoot.resolve(candidate)
        return runCatching { absolute.toAbsolutePath().normalize() }.getOrNull()
    }

    private fun readDocsDirValue(configPath: Path): String? {
        val regex = Regex("""^\s*docs_dir\s*:\s*(.+?)\s*(?:#.*)?$""")
        return runCatching {
            Files.readAllLines(configPath)
                .asSequence()
                .mapNotNull { line -> regex.find(line)?.groupValues?.getOrNull(1) }
                .map(::parseYamlScalar)
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseYamlScalar(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1).replace("''", "'").trim()
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .trim()
        }
        return trimmed.substringBefore('#').trim()
    }

    private fun normalizePath(path: Path): String {
        val normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/')
        return if (isFileSystemCaseSensitive()) normalized else normalized.lowercase(Locale.ROOT)
    }

    private fun isFileSystemCaseSensitive(): Boolean {
        return com.intellij.openapi.util.SystemInfoRt.isFileSystemCaseSensitive
    }

    private data class ConfigScope(
        val configPath: Path,
        val docsDirPath: Path?,
        val invalidReason: String?,
    ) {
        val isValid: Boolean
            get() = docsDirPath != null && invalidReason == null
    }
}
