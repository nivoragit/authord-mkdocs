package com.authord.mkdocs.runtime

import org.yaml.snakeyaml.Yaml
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
    val uvExecutablePath: String,
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
    private val uvExecutableProvider: UvExecutableProvider,
) {
    companion object {
        private val builtinPluginNames = setOf("search")
    }

    /**
     * Backward-compatible constructor that defaults to shell `uv` resolution.
     */
    constructor(commandRunner: CommandRunner) : this(
        commandRunner = commandRunner,
        uvExecutableProvider = StaticUvExecutableProvider(),
    )

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
        val uvResolution = uvExecutableProvider.resolve(projectPath)
        if (!uvResolution.success) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                uvExecutablePath = "",
                executedCommands = emptyList(),
                skipped = false,
                errorMessage = uvResolution.errorMessage,
            )
        }
        val uvExecutable = uvResolution.executablePath
        val requiredPackages = resolveRequiredPackages(projectPath)
        val cachedPackages = bootstrappedProjectPackages[projectPath]

        if (cachedPackages != null && cachedPackages.containsAll(requiredPackages)) {
            return BootstrapResult(
                success = true,
                runtimePath = runtimePath,
                uvExecutablePath = uvExecutable,
                executedCommands = emptyList(),
                skipped = true,
            )
        }

        val installCommand = buildList {
            addAll(listOf(uvExecutable, "pip", "install", "--python", runtimePath))
            addAll(requiredPackages)
        }
        val executed = mutableListOf<List<String>>()

        if (!runtimeDirectory.exists()) {
            val setupCommand = listOf(uvExecutable, "venv", runtimePath)
            val setupResult = commandRunner.run(setupCommand, projectPath)
            executed += setupCommand
            if (setupResult.exitCode != 0 && !isExistingRuntimeError(setupResult)) {
                return BootstrapResult(
                    success = false,
                    runtimePath = runtimePath,
                    uvExecutablePath = uvExecutable,
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
                uvExecutablePath = uvExecutable,
                executedCommands = executed,
                skipped = false,
                errorMessage = installResult.stderr.ifBlank { "Failed to install mkdocs" },
            )
        }

        bootstrappedProjectPackages[projectPath] = requiredPackages.toSet()
        return BootstrapResult(
            success = true,
            runtimePath = runtimePath,
            uvExecutablePath = uvExecutable,
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
        val config = loadMkdocsConfig(projectPath)
        if (usesMaterialTheme(config)) {
            packages += "mkdocs-material"
        }
        packages += resolvePluginPackages(config)
        return packages.distinct()
    }

    private fun loadMkdocsConfig(projectPath: String): Map<*, *>? {
        val configPath = resolveMkdocsConfigPath(projectPath) ?: return null
        return runCatching {
            Files.newBufferedReader(configPath).use { reader ->
                Yaml().load<Any?>(reader)
            } as? Map<*, *>
        }.getOrNull()
    }

    private fun usesMaterialTheme(config: Map<*, *>?): Boolean {
        val rawTheme = config?.get("theme") ?: return false
        return when (rawTheme) {
            is String -> isMaterialValue(rawTheme)
            is Map<*, *> -> isMaterialValue(rawTheme["name"]?.toString().orEmpty())
            else -> false
        }
    }

    private fun resolvePluginPackages(config: Map<*, *>?): List<String> {
        val pluginNames = extractPluginNames(config?.get("plugins"))
        return pluginNames.mapNotNull(::pluginPackageFor).distinct()
    }

    private fun extractPluginNames(rawPlugins: Any?): List<String> {
        return when (rawPlugins) {
            null -> emptyList()
            is String -> listOfNotNull(normalizePluginName(rawPlugins))
            is List<*> -> rawPlugins.flatMap { entry ->
                when (entry) {
                    is String -> listOfNotNull(normalizePluginName(entry))
                    is Map<*, *> -> entry.keys.mapNotNull { key -> normalizePluginName(key?.toString().orEmpty()) }
                    else -> emptyList()
                }
            }

            is Map<*, *> -> rawPlugins.keys.mapNotNull { key -> normalizePluginName(key?.toString().orEmpty()) }
            else -> emptyList()
        }
    }

    private fun pluginPackageFor(pluginName: String): String? {
        val normalizedName = pluginName.replace('_', '-')
        if (normalizedName in builtinPluginNames) {
            return null
        }
        if (normalizedName.startsWith("mkdocs-")) {
            return normalizedName
        }
        return "mkdocs-$normalizedName"
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

    private fun normalizePluginName(rawValue: String): String? {
        val normalized = rawValue
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("'")
            .removeSuffix("'")
            .trim()
            .lowercase()
        return normalized.ifBlank { null }
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
}
