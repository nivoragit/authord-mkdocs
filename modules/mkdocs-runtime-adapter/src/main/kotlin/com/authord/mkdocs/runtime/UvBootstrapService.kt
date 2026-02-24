package com.authord.mkdocs.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

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
 * Bootstrap failure categories surfaced for diagnostics and UX messaging.
 */
enum class BootstrapFailureCategory {
    NONE,
    UV_RESOLUTION,
    VENV_SETUP,
    BASE_INSTALL,
    GET_DEPS,
    DEPENDENCY_INSTALL,
    FAST_VERIFY,
    STRICT_VERIFY,
}

/**
 * Outcome for dependency discovery/install via `mkdocs get-deps`.
 */
enum class DependencyBootstrapOutcome {
    NOT_RUN,
    SUCCESS,
    FAILED,
    BYPASSED,
}

/**
 * Verification mode executed for the latest bootstrap call.
 */
enum class BootstrapVerificationMode {
    NONE,
    FAST,
    STRICT,
}

/**
 * Compact bootstrap diagnostics used by activation/error reporting.
 */
data class BootstrapDiagnostics(
    val pythonExecutable: String = "",
    val pythonVersion: String = "",
    val mkdocsVersion: String = "",
    val dependencyFingerprint: String = "",
    val uvDescriptor: String = "",
    val getDepsOutcome: DependencyBootstrapOutcome = DependencyBootstrapOutcome.NOT_RUN,
    val fallbackDependencySource: String = "",
    val verificationMode: BootstrapVerificationMode = BootstrapVerificationMode.NONE,
    val verificationSucceeded: Boolean? = null,
    val verificationDetails: String = "",
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
    val diagnostics: BootstrapDiagnostics = BootstrapDiagnostics(),
    val canStartAnyway: Boolean = false,
    val failureCategory: BootstrapFailureCategory = BootstrapFailureCategory.NONE,
)

private data class FastVerificationResult(
    val success: Boolean,
    val pythonVersion: String,
    val mkdocsVersion: String,
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
 * - Repeated calls for same project path skip duplicate setup when
 *   dependency fingerprint is unchanged.
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
    private val strictVerificationRequestedProjects = mutableSetOf<String>()
    private val dependencyBypassRequestedProjects = mutableSetOf<String>()
    private val forcedBootstrapProjects = mutableSetOf<String>()

    /**
     * Requests strict verification on the next bootstrap call for a project.
     */
    open fun requestStrictVerification(projectPath: String) {
        strictVerificationRequestedProjects += normalizeProjectKey(projectPath)
    }

    /**
     * Forces dependency setup to run again on next bootstrap and enables strict verification.
     */
    open fun requestDependencyRetry(projectPath: String) {
        val key = normalizeProjectKey(projectPath)
        forcedBootstrapProjects += key
        strictVerificationRequestedProjects += key
    }

    /**
     * Allows one-time bypass when `mkdocs get-deps` fails so runtime startup may proceed.
     */
    open fun requestStartAnyway(projectPath: String) {
        val key = normalizeProjectKey(projectPath)
        dependencyBypassRequestedProjects += key
        forcedBootstrapProjects += key
    }

    /**
     * Ensures project runtime exists and required packages are installed.
     *
     * The bootstrap process:
     * 1. Creates a virtual environment (if needed).
     * 2. Installs `mkdocs` (base package) and any `requirements*.txt` files.
     * 3. Runs `mkdocs get-deps` to discover all dependencies from `mkdocs.yml`.
     * 4. Installs the discovered dependencies.
     * 5. Runs fast verification and, when triggered, strict verification.
     *
     * @param projectPath root project path.
     * @return bootstrap status, executed commands, and error details on failure.
     */
    open fun bootstrap(projectPath: String): BootstrapResult {
        val runtimeDirectory = Path.of(projectPath).resolve(".mkdocs-plugin-venv")
        val runtimePath = runtimeDirectory.toString()
        val projectKey = normalizeProjectKey(projectPath)

        val uvResolution = uvExecutableProvider.resolve(projectPath)
        if (!uvResolution.success) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                uvExecutablePath = "",
                executedCommands = emptyList(),
                skipped = false,
                errorMessage = uvResolution.errorMessage,
                diagnostics = BootstrapDiagnostics(uvDescriptor = uvResolution.errorMessage),
                failureCategory = BootstrapFailureCategory.UV_RESOLUTION,
            )
        }
        val uvExecutable = uvResolution.executablePath

        val forceBootstrap = forcedBootstrapProjects.remove(projectKey)
        val strictVerificationRequested = strictVerificationRequestedProjects.remove(projectKey)
        val allowGetDepsBypass = dependencyBypassRequestedProjects.remove(projectKey)

        val currentFingerprint = computeDependencyFingerprint(projectPath, runtimeDirectory, uvExecutable)
        val cachedFingerprint = bootstrappedProjectHashes[projectKey]
        val runtimeExists = runtimeDirectory.exists()
        val dependencyFingerprintChanged = cachedFingerprint == null || cachedFingerprint != currentFingerprint
        val shouldSkipDependencyInstall = !forceBootstrap && runtimeExists && !dependencyFingerprintChanged

        val executed = mutableListOf<List<String>>()
        var pythonExecutable = resolveVenvPython(runtimeDirectory)
        var pythonVersion = ""
        var mkdocsVersion = ""
        var getDepsOutcome = DependencyBootstrapOutcome.NOT_RUN
        val fallbackDependencySource = resolveFallbackDependencySource(projectPath)
        var verificationMode = BootstrapVerificationMode.NONE
        var verificationSucceeded: Boolean? = null
        var verificationDetails = ""

        if (!shouldSkipDependencyInstall) {
            if (!runtimeExists) {
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
                        diagnostics = BootstrapDiagnostics(
                            pythonExecutable = pythonExecutable,
                            dependencyFingerprint = currentFingerprint,
                            uvDescriptor = describeUvExecutable(uvExecutable),
                            fallbackDependencySource = fallbackDependencySource,
                        ),
                        failureCategory = BootstrapFailureCategory.VENV_SETUP,
                    )
                }
            }

            pythonExecutable = resolveVenvPython(runtimeDirectory)

            val requirementsFiles = resolveRequirementsPaths(projectPath)
            val baseInstallCommand = buildList {
                addAll(listOf(uvExecutable, "pip", "install", "--python", pythonExecutable, "mkdocs"))
                requirementsFiles.forEach { requirementsFile ->
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
                    diagnostics = BootstrapDiagnostics(
                        pythonExecutable = pythonExecutable,
                        dependencyFingerprint = currentFingerprint,
                        uvDescriptor = describeUvExecutable(uvExecutable),
                        fallbackDependencySource = fallbackDependencySource,
                    ),
                    failureCategory = BootstrapFailureCategory.BASE_INSTALL,
                )
            }

            val getDepsCommand = listOf(pythonExecutable, "-m", "mkdocs", "get-deps")
            val getDepsResult = commandRunner.run(getDepsCommand, projectPath)
            executed += getDepsCommand

            if (getDepsResult.exitCode != 0) {
                getDepsOutcome = if (allowGetDepsBypass) {
                    DependencyBootstrapOutcome.BYPASSED
                } else {
                    DependencyBootstrapOutcome.FAILED
                }

                if (!allowGetDepsBypass) {
                    val getDepsError = combinedErrorOutput(getDepsResult)
                    return BootstrapResult(
                        success = false,
                        runtimePath = runtimePath,
                        uvExecutablePath = uvExecutable,
                        executedCommands = executed,
                        skipped = false,
                        errorMessage = getDepsError.ifBlank { "Failed to resolve MkDocs dependencies" },
                        diagnostics = BootstrapDiagnostics(
                            pythonExecutable = pythonExecutable,
                            dependencyFingerprint = currentFingerprint,
                            uvDescriptor = describeUvExecutable(uvExecutable),
                            getDepsOutcome = getDepsOutcome,
                            fallbackDependencySource = fallbackDependencySource,
                        ),
                        canStartAnyway = true,
                        failureCategory = BootstrapFailureCategory.GET_DEPS,
                    )
                }
            } else {
                getDepsOutcome = DependencyBootstrapOutcome.SUCCESS
                val discoveredDeps = getDepsResult.stdout
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (discoveredDeps.isNotEmpty()) {
                    val depsInstallCommand = buildList {
                        addAll(listOf(uvExecutable, "pip", "install", "--python", pythonExecutable))
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
                            diagnostics = BootstrapDiagnostics(
                                pythonExecutable = pythonExecutable,
                                dependencyFingerprint = currentFingerprint,
                                uvDescriptor = describeUvExecutable(uvExecutable),
                                getDepsOutcome = getDepsOutcome,
                                fallbackDependencySource = fallbackDependencySource,
                            ),
                            failureCategory = BootstrapFailureCategory.DEPENDENCY_INSTALL,
                        )
                    }
                }
            }
        }

        val fastVerification = runFastVerification(
            pythonExecutable = pythonExecutable,
            projectPath = projectPath,
            executedCommands = executed,
        )
        verificationMode = BootstrapVerificationMode.FAST
        verificationSucceeded = fastVerification.success
        verificationDetails = if (fastVerification.success) {
            "fast verification succeeded"
        } else {
            fastVerification.errorMessage
        }
        pythonVersion = fastVerification.pythonVersion
        mkdocsVersion = fastVerification.mkdocsVersion

        if (!fastVerification.success) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                uvExecutablePath = uvExecutable,
                executedCommands = executed,
                skipped = shouldSkipDependencyInstall,
                errorMessage = fastVerification.errorMessage,
                diagnostics = BootstrapDiagnostics(
                    pythonExecutable = pythonExecutable,
                    pythonVersion = pythonVersion,
                    mkdocsVersion = mkdocsVersion,
                    dependencyFingerprint = currentFingerprint,
                    uvDescriptor = describeUvExecutable(uvExecutable),
                    getDepsOutcome = getDepsOutcome,
                    fallbackDependencySource = fallbackDependencySource,
                    verificationMode = verificationMode,
                    verificationSucceeded = verificationSucceeded,
                    verificationDetails = verificationDetails,
                ),
                failureCategory = BootstrapFailureCategory.FAST_VERIFY,
            )
        }

        val shouldRunStrictVerification = dependencyFingerprintChanged || strictVerificationRequested || forceBootstrap
        if (shouldRunStrictVerification) {
            val strictVerificationCommand = listOf(pythonExecutable, "-m", "mkdocs", "build", "--strict")
            val strictVerificationResult = commandRunner.run(strictVerificationCommand, projectPath)
            executed += strictVerificationCommand

            verificationMode = BootstrapVerificationMode.STRICT
            verificationSucceeded = strictVerificationResult.exitCode == 0
            verificationDetails = if (strictVerificationResult.exitCode == 0) {
                "strict verification succeeded"
            } else {
                combinedErrorOutput(strictVerificationResult).ifBlank { "mkdocs build --strict failed" }
            }

            if (strictVerificationResult.exitCode != 0) {
                return BootstrapResult(
                    success = false,
                    runtimePath = runtimePath,
                    uvExecutablePath = uvExecutable,
                    executedCommands = executed,
                    skipped = shouldSkipDependencyInstall,
                    errorMessage = verificationDetails,
                    diagnostics = BootstrapDiagnostics(
                        pythonExecutable = pythonExecutable,
                        pythonVersion = pythonVersion,
                        mkdocsVersion = mkdocsVersion,
                        dependencyFingerprint = currentFingerprint,
                        uvDescriptor = describeUvExecutable(uvExecutable),
                        getDepsOutcome = getDepsOutcome,
                        fallbackDependencySource = fallbackDependencySource,
                        verificationMode = verificationMode,
                        verificationSucceeded = verificationSucceeded,
                        verificationDetails = verificationDetails,
                    ),
                    canStartAnyway = true,
                    failureCategory = BootstrapFailureCategory.STRICT_VERIFY,
                )
            }
        }

        bootstrappedProjectHashes[projectKey] = currentFingerprint
        return BootstrapResult(
            success = true,
            runtimePath = runtimePath,
            uvExecutablePath = uvExecutable,
            executedCommands = executed,
            skipped = shouldSkipDependencyInstall,
            diagnostics = BootstrapDiagnostics(
                pythonExecutable = pythonExecutable,
                pythonVersion = pythonVersion,
                mkdocsVersion = mkdocsVersion,
                dependencyFingerprint = currentFingerprint,
                uvDescriptor = describeUvExecutable(uvExecutable),
                getDepsOutcome = getDepsOutcome,
                fallbackDependencySource = fallbackDependencySource,
                verificationMode = verificationMode,
                verificationSucceeded = verificationSucceeded,
                verificationDetails = verificationDetails,
            ),
        )
    }

    private fun runFastVerification(
        pythonExecutable: String,
        projectPath: String,
        executedCommands: MutableList<List<String>>,
    ): FastVerificationResult {
        val pythonVersionCommand = listOf(
            pythonExecutable,
            "-c",
            "import platform; print(platform.python_version())",
        )
        val pythonVersionResult = commandRunner.run(pythonVersionCommand, projectPath)
        executedCommands += pythonVersionCommand
        if (pythonVersionResult.exitCode != 0) {
            val error = combinedErrorOutput(pythonVersionResult)
            return FastVerificationResult(
                success = false,
                pythonVersion = "",
                mkdocsVersion = "",
                errorMessage = error.ifBlank { "Failed to resolve Python version from runtime interpreter." },
            )
        }

        val mkdocsVersionCommand = listOf(pythonExecutable, "-m", "mkdocs", "--version")
        val mkdocsVersionResult = commandRunner.run(mkdocsVersionCommand, projectPath)
        executedCommands += mkdocsVersionCommand
        if (mkdocsVersionResult.exitCode != 0) {
            val error = combinedErrorOutput(mkdocsVersionResult)
            return FastVerificationResult(
                success = false,
                pythonVersion = parsePythonVersion(pythonVersionResult.stdout),
                mkdocsVersion = "",
                errorMessage = error.ifBlank { "Failed to verify MkDocs installation in runtime interpreter." },
            )
        }

        return FastVerificationResult(
            success = true,
            pythonVersion = parsePythonVersion(pythonVersionResult.stdout),
            mkdocsVersion = parseMkdocsVersion(mkdocsVersionResult.stdout),
        )
    }

    private fun parsePythonVersion(rawOutput: String): String {
        val compact = rawOutput
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return compact.removePrefix("Python ").ifBlank { compact }
    }

    private fun parseMkdocsVersion(rawOutput: String): String {
        val compact = rawOutput
            .lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val match = Regex("""version\s+([^\s,]+)""", RegexOption.IGNORE_CASE).find(compact)
        return match?.groupValues?.getOrNull(1)?.ifBlank { compact } ?: compact
    }

    private fun combinedErrorOutput(result: CommandResult): String {
        val merged = buildString {
            if (result.stderr.isNotBlank()) {
                append(result.stderr.trim())
            }
            if (result.stdout.isNotBlank()) {
                if (isNotEmpty()) {
                    append('\n')
                }
                append(result.stdout.trim())
            }
        }
        return merged.trim()
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

    private fun resolveRequirementsPaths(projectPath: String): List<Path> {
        val projectRoot = Path.of(projectPath)
        if (!projectRoot.exists()) {
            return emptyList()
        }

        val requirements = mutableListOf<Path>()
        runCatching {
            Files.newDirectoryStream(projectRoot).use { entries ->
                for (entry in entries) {
                    val fileName = entry.fileName.toString()
                    if (
                        entry.isRegularFile() &&
                        fileName.startsWith("requirements", ignoreCase = true) &&
                        fileName.endsWith(".txt", ignoreCase = true)
                    ) {
                        requirements.add(entry)
                    }
                }
            }
        }
        return requirements.distinct().sortedBy { it.fileName.toString().lowercase() }
    }

    private fun resolvePyprojectPath(projectPath: String): Path? {
        val pyproject = Path.of(projectPath).resolve("pyproject.toml")
        return if (pyproject.exists() && pyproject.isRegularFile()) pyproject else null
    }

    private fun resolveFallbackDependencySource(projectPath: String): String {
        val requirementFiles = resolveRequirementsPaths(projectPath)
        if (requirementFiles.isNotEmpty()) {
            return requirementFiles.joinToString(",") { it.fileName.toString() }
        }
        val pyproject = resolvePyprojectPath(projectPath)
        if (pyproject != null) {
            return pyproject.fileName.toString()
        }
        return "none"
    }

    /**
     * Computes a hash of dependency declarations and runtime metadata to
     * determine if a re-bootstrap is needed.
     */
    private fun computeDependencyFingerprint(projectPath: String, runtimeDirectory: Path, uvExecutable: String): String {
        val digest = MessageDigest.getInstance("SHA-256")

        collectTrackedDependencyFiles(projectPath).forEach { trackedFile ->
            runCatching {
                if (Files.isRegularFile(trackedFile)) {
                    digest.update(trackedFile.toString().toByteArray())
                    digest.update(Files.readAllBytes(trackedFile))
                }
            }
        }

        val runtimePython = resolveVenvPython(runtimeDirectory)
        digest.update(runtimePython.toByteArray())

        val pyvenvCfg = runtimeDirectory.resolve("pyvenv.cfg")
        runCatching {
            if (Files.isRegularFile(pyvenvCfg)) {
                digest.update(Files.readAllBytes(pyvenvCfg))
            }
        }

        val uvDescriptor = describeUvExecutable(uvExecutable)
        digest.update(uvExecutable.toByteArray())
        digest.update(uvDescriptor.toByteArray())

        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun collectTrackedDependencyFiles(projectPath: String): List<Path> {
        val root = Path.of(projectPath)
        val tracked = mutableListOf<Path>()

        val mkdocsYml = root.resolve("mkdocs.yml")
        if (mkdocsYml.exists() && mkdocsYml.isRegularFile()) {
            tracked.add(mkdocsYml)
        }

        val mkdocsYaml = root.resolve("mkdocs.yaml")
        if (mkdocsYaml.exists() && mkdocsYaml.isRegularFile()) {
            tracked.add(mkdocsYaml)
        }

        tracked.addAll(resolveRequirementsPaths(projectPath))
        resolvePyprojectPath(projectPath)?.let { tracked.add(it) }

        val lockFiles = listOf(
            "uv.lock",
            "poetry.lock",
            "pdm.lock",
            "Pipfile.lock",
            "requirements.lock",
        )
        lockFiles.forEach { lockName ->
            val lockPath = root.resolve(lockName)
            if (lockPath.exists() && lockPath.isRegularFile()) {
                tracked.add(lockPath)
            }
        }

        return tracked.distinct().sortedBy { it.toString() }
    }

    private fun describeUvExecutable(uvExecutable: String): String {
        val uvPath = runCatching { Path.of(uvExecutable) }.getOrNull() ?: return uvExecutable
        if (!Files.isRegularFile(uvPath)) {
            return uvExecutable
        }

        val size = runCatching { Files.size(uvPath) }.getOrDefault(-1L)
        val modified = runCatching { Files.getLastModifiedTime(uvPath).toMillis() }.getOrDefault(-1L)
        return "${uvPath.toAbsolutePath().normalize()}|size=$size|mtime=$modified"
    }

    private fun normalizeProjectKey(projectPath: String): String {
        return runCatching { Path.of(projectPath).toAbsolutePath().normalize().toString() }
            .getOrDefault(projectPath)
    }
}
