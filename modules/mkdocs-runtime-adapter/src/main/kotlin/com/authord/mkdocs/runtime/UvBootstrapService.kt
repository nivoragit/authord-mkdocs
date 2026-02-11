package com.authord.mkdocs.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/** API version for runtime command-runner seam. */
const val COMMAND_RUNNER_API_VERSION: String = "1.0.0"

/**
 * Command runner contract used by runtime bootstrap orchestration.
 *
 * API Version: [COMMAND_RUNNER_API_VERSION]
 */
fun interface CommandRunner {
    /**
     * Runs a process command in the given working directory path.
     *
     * @param command tokenized command list.
     * @param workingDir absolute or project-relative working directory string.
     * @return process execution details.
     */
    fun run(command: List<String>, workingDir: String): CommandResult
}

/**
 * Process command execution result envelope.
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
)

/**
 * Runtime bootstrap result envelope.
 */
data class BootstrapResult(
    val success: Boolean,
    val runtimePath: String,
    val executedCommands: List<List<String>>,
    val skipped: Boolean,
    val errorMessage: String = "",
)

/**
 * Bootstraps a plugin-managed runtime using `uv` and installs `mkdocs`.
 *
 * Usage:
 * - Call once per activation attempt.
 * - Repeated calls for same project path skip duplicate setup.
 */
class UvBootstrapService(
    private val commandRunner: CommandRunner,
) {
    private val bootstrappedProjectPackages = mutableMapOf<String, Set<String>>()

    /**
     * Ensures project runtime exists and required packages are installed.
     *
     * @param projectPath root project path.
     * @return bootstrap status, executed commands, and error details on failure.
     */
    fun bootstrap(projectPath: String): BootstrapResult {
        val runtimeDirectory = Path.of(projectPath).resolve(".mkdocs-plugin-venv")
        val runtimePath = runtimeDirectory.toString()
        val requiredPackages = resolveRequiredPackages(projectPath)
        val cachedPackages = bootstrappedProjectPackages[projectPath]

        if (cachedPackages != null && cachedPackages.containsAll(requiredPackages)) {
            return BootstrapResult(
                success = true,
                runtimePath = runtimePath,
                executedCommands = emptyList(),
                skipped = true,
            )
        }

        val installCommand = buildList {
            addAll(listOf("uv", "pip", "install", "--python", runtimePath))
            addAll(requiredPackages)
        }
        val executed = mutableListOf<List<String>>()

        if (!runtimeDirectory.exists()) {
            val setupCommand = listOf("uv", "venv", runtimePath)
            val setupResult = commandRunner.run(setupCommand, projectPath)
            executed += setupCommand
            if (setupResult.exitCode != 0 && !isExistingRuntimeError(setupResult)) {
                return BootstrapResult(
                    success = false,
                    runtimePath = runtimePath,
                    executedCommands = executed,
                    skipped = false,
                    errorMessage = setupResult.stderr.ifBlank { "Failed to create runtime" },
                )
            }
        }

        val installResult = commandRunner.run(installCommand, projectPath)
        executed += installCommand
        if (installResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                executedCommands = executed,
                skipped = false,
                errorMessage = installResult.stderr.ifBlank { "Failed to install mkdocs" },
            )
        }

        bootstrappedProjectPackages[projectPath] = requiredPackages.toSet()
        return BootstrapResult(
            success = true,
            runtimePath = runtimePath,
            executedCommands = executed,
            skipped = false,
        )
    }

    private fun isExistingRuntimeError(result: CommandResult): Boolean {
        val normalized = buildString {
            append(result.stderr)
            append('\n')
            append(result.stdout)
        }.lowercase()
        return "virtual environment already exists" in normalized || "already exists at" in normalized
    }

    private fun resolveRequiredPackages(projectPath: String): List<String> {
        val packages = mutableListOf("mkdocs")
        if (usesMaterialTheme(projectPath)) {
            packages += "mkdocs-material"
        }
        return packages
    }

    private fun usesMaterialTheme(projectPath: String): Boolean {
        val configPath = resolveMkdocsConfigPath(projectPath) ?: return false
        val content = runCatching { Files.readString(configPath) }.getOrElse { return false }
        return hasMaterialTheme(content)
    }

    private fun resolveMkdocsConfigPath(projectPath: String): Path? {
        val root = Path.of(projectPath)
        val yaml = root.resolve("mkdocs.yml")
        if (yaml.exists()) {
            return yaml
        }

        val ymlAlt = root.resolve("mkdocs.yaml")
        return if (ymlAlt.exists()) ymlAlt else null
    }

    // We intentionally avoid a YAML dependency for this MVP and parse only the theme name patterns we need.
    private fun hasMaterialTheme(content: String): Boolean {
        val lines = content.lineSequence()
            .map { it.substringBefore('#').trimEnd() }
            .toList()

        var inThemeBlock = false
        var themeIndent = -1

        for (rawLine in lines) {
            if (rawLine.isBlank()) {
                continue
            }

            val indent = rawLine.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) rawLine.length else it }
            val line = rawLine.trim()

            if (inThemeBlock && indent <= themeIndent) {
                inThemeBlock = false
            }

            if (!inThemeBlock && line.startsWith("theme:")) {
                val value = line.removePrefix("theme:").trim()
                if (value.isBlank()) {
                    inThemeBlock = true
                    themeIndent = indent
                    continue
                }
                if (inlineThemeDeclaresMaterial(value)) {
                    return true
                }
                continue
            }

            if (inThemeBlock && line.startsWith("name:")) {
                val value = line.removePrefix("name:").trim()
                if (isMaterialValue(value)) {
                    return true
                }
            }
        }

        return false
    }

    private fun inlineThemeDeclaresMaterial(themeValue: String): Boolean {
        if (isMaterialValue(themeValue)) {
            return true
        }

        if (!themeValue.startsWith("{") || !themeValue.endsWith("}")) {
            return false
        }

        val inlineEntries = themeValue
            .removePrefix("{")
            .removeSuffix("}")
            .split(',')
            .map { it.trim() }

        val nameEntry = inlineEntries.firstOrNull { it.startsWith("name:") } ?: return false
        return isMaterialValue(nameEntry.removePrefix("name:").trim())
    }

    private fun isMaterialValue(rawValue: String): Boolean {
        val normalized = rawValue
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("'")
            .removeSuffix("'")
            .trim()
        return normalized == "material"
    }
}
