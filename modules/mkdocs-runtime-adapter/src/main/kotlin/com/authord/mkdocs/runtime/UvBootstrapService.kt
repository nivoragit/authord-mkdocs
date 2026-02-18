package com.authord.mkdocs.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists

/**
 * Runs a process command in a given working directory.
 */
fun interface CommandRunner {
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
 * Uses `mkdocs get-deps` to automatically discover and install all
 * dependencies declared in the project's `mkdocs.yml`, eliminating
 * the need for manual package name mappings.
 *
 * Usage:
 * - Call once per activation attempt.
 * - Repeated calls for same project path skip duplicate setup.
 */
open class UvBootstrapService(
    private val commandRunner: CommandRunner,
    private val uvExecutableProvider: UvExecutableProvider,
) {
    /**
     * Backward-compatible constructor that defaults to shell `uv` resolution.
     */
    constructor(commandRunner: CommandRunner) : this(
        commandRunner = commandRunner,
        uvExecutableProvider = StaticUvExecutableProvider(),
    )

    private val bootstrappedProjectHashes = mutableMapOf<String, String>()

    /**
     * Ensures project runtime exists and required packages are installed.
     *
     * The bootstrap process:
     * 1. Creates a virtual environment (if needed).
     * 2. Installs `mkdocs` (base package) and any `requirements.txt`.
     * 3. Runs `mkdocs get-deps` to discover all dependencies from `mkdocs.yml`.
     * 4. Installs the discovered dependencies.
     *
     * @param projectPath root project path.
     * @return bootstrap status, executed commands, and error details on failure.
     */
    open fun bootstrap(projectPath: String): BootstrapResult {
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

        // Check cache: skip if config hasn't changed
        val currentHash = computeConfigHash(projectPath)
        val cachedHash = bootstrappedProjectHashes[projectPath]
        if (cachedHash != null && cachedHash == currentHash && runtimeDirectory.exists()) {
            return BootstrapResult(
                success = true,
                runtimePath = runtimePath,
                uvExecutablePath = uvExecutable,
                executedCommands = emptyList(),
                skipped = true,
            )
        }

        val executed = mutableListOf<List<String>>()

        // Step 1: Create venv if needed
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

        // Step 2: Install mkdocs base package (+ requirements.txt if present)
        val baseInstallCommand = buildList {
            addAll(listOf(uvExecutable, "pip", "install", "--python", runtimePath, "mkdocs"))
            val requirementsFile = resolveRequirementsPath(projectPath)
            if (requirementsFile != null) {
                add("-r")
                add(requirementsFile.toString())
            }
        }
        val baseInstallResult = commandRunner.run(baseInstallCommand, projectPath)
        executed += baseInstallCommand
        if (baseInstallResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                uvExecutablePath = uvExecutable,
                executedCommands = executed,
                skipped = false,
                errorMessage = baseInstallResult.stderr.ifBlank { "Failed to install mkdocs" },
            )
        }

        // Step 3: Run `mkdocs get-deps` to discover all required packages
        val pythonPath = resolveVenvPython(runtimeDirectory)
        val getDepsCommand = listOf(pythonPath, "-m", "mkdocs", "get-deps")
        val getDepsResult = commandRunner.run(getDepsCommand, projectPath)
        executed += getDepsCommand

        // Step 4: Install discovered dependencies (if any)
        if (getDepsResult.exitCode == 0) {
            val discoveredDeps = getDepsResult.stdout
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            if (discoveredDeps.isNotEmpty()) {
                val depsInstallCommand = buildList {
                    addAll(listOf(uvExecutable, "pip", "install", "--python", runtimePath))
                    addAll(discoveredDeps)
                }
                val depsInstallResult = commandRunner.run(depsInstallCommand, projectPath)
                executed += depsInstallCommand
                if (depsInstallResult.exitCode != 0) {
                    return BootstrapResult(
                        success = false,
                        runtimePath = runtimePath,
                        uvExecutablePath = uvExecutable,
                        executedCommands = executed,
                        skipped = false,
                        errorMessage = depsInstallResult.stderr.ifBlank { "Failed to install mkdocs dependencies" },
                    )
                }
            }
        }
        // If get-deps fails (e.g., bad config), we still succeed with just mkdocs installed.
        // The user will see the MkDocs error when they try to serve.

        bootstrappedProjectHashes[projectPath] = currentHash
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

    private fun resolveVenvPython(runtimeDirectory: Path): String {
        // On Windows, python is at <venv>/Scripts/python.exe
        val winPython = runtimeDirectory.resolve("Scripts").resolve("python.exe")
        if (winPython.exists()) {
            return winPython.toString()
        }
        // On macOS/Linux (or fallback), python is at <venv>/bin/python
        return runtimeDirectory.resolve("bin").resolve("python").toString()
    }

    private fun resolveRequirementsPath(projectPath: String): Path? {
        val requirements = Path.of(projectPath).resolve("requirements.txt")
        return if (requirements.exists()) requirements else null
    }

    /**
     * Computes a hash of the project's mkdocs config and requirements to
     * determine if a re-bootstrap is needed.
     */
    private fun computeConfigHash(projectPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val configPath = resolveMkdocsConfigPath(projectPath)
        if (configPath != null && Files.isRegularFile(configPath)) {
            digest.update(Files.readAllBytes(configPath))
        }
        val requirementsPath = resolveRequirementsPath(projectPath)
        if (requirementsPath != null && Files.isRegularFile(requirementsPath)) {
            digest.update(Files.readAllBytes(requirementsPath))
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
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
