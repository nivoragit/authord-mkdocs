package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.BootstrapDiagnostics
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.RuntimeProcessDiagnostics
import com.authord.mkdocs.runtime.RuntimeServerConfig
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ui.intellij.AuthordUiBundle
import com.authord.mkdocs.ui.intellij.PreviewStartupFailure
import com.authord.mkdocs.ui.intellij.PreviewStartupFailureClassifier
import com.authord.mkdocs.ui.intellij.PreviewStartupFailureContext
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Probes HTTP readiness for a resolved base URL.
 */
fun interface HttpReadinessProbe {
    /**
     * Returns `true` when the URL returns any HTTP status response.
     */
    fun isReady(baseUrl: String): Boolean
}

/**
 * Lightweight HTTP readiness probe using [HttpURLConnection].
 */
open class HttpURLConnectionReadinessProbe(
    private val connectTimeoutMillis: Int = 350,
    private val readTimeoutMillis: Int = 350,
) : HttpReadinessProbe {
    override fun isReady(baseUrl: String): Boolean {
        val connection = runCatching {
            URI.create(baseUrl).toURL().openConnection() as HttpURLConnection
        }.getOrNull() ?: return false

        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.connect()
            val status = connection.responseCode
            status in 100..599
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}

private fun defaultPluginEnvironmentRoot(): Path {
    val systemPath = runCatching { PathManager.getSystemPath() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
    val base = systemPath?.let(Path::of) ?: Path.of(System.getProperty("java.io.tmpdir"))
    return base.resolve("authord")
}

/**
 * Result returned by plugin activation flow.
 */
data class ActivationDiagnostics(
    val pythonExecutable: String = "",
    val pythonVersion: String = "",
    val mkdocsVersion: String = "",
    val bootstrapResultSummary: String = "",
    val verificationSummary: String = "",
    val classifiedErrorCategory: String = "",
    val suggestedPackage: String = "",
    val dependencyDeclarationHint: String = "",
)

/**
 * Result returned by plugin activation flow.
 */
data class ActivationResult(
    val success: Boolean,
    val reason: ActivationFailureReason? = null,
    val message: String = "",
    val previewUrl: String = "",
    val diagnostics: ActivationDiagnostics = ActivationDiagnostics(),
    val startAnywayAvailable: Boolean = false,
)

/**
 * Orchestrates first activation bootstrap, runtime startup, and preview opening.
 */
class PluginActivationService(
    private val bootstrapService: UvBootstrapService,
    private val processManager: MkdocsProcessManager,
    @Suppress("UNUSED_PARAMETER")
    baseUrlDetector: BaseUrlDetector,
    private val previewPaneCoordinator: PreviewPaneCoordinator,
    private val errorPresenter: ActivationErrorPresenter,
    private val isDarkIdeTheme: () -> Boolean = { false },
    private val readinessProbe: HttpReadinessProbe = HttpURLConnectionReadinessProbe(),
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
    private val maxStartupAttempts: Int = 2,
    private val startupProbeTimeoutMillis: Long = 45_000L,
    private val startupPollIntervalMillis: Long = 150L,
    private val pluginEnvironmentRootProvider: () -> Path = ::defaultPluginEnvironmentRoot,
) {
    private data class StartupAttemptFailure(
        val baseUrl: String,
        val failureSummary: String,
        val diagnostics: RuntimeProcessDiagnostics?,
        val startupOutput: String,
    )

    private data class ClassifiedStartupFailureDetails(
        val details: String,
        val rawOutput: String,
        val classifiedFailure: PreviewStartupFailure,
    )

    private sealed interface StartupReadiness {
        data class Ready(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class ProcessExited(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class TimedOut(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness
    }

    private val siteNameKeyRegex = Regex("""^\s*site_name\s*:""")
    private val docsDirKeyRegex = Regex("""^\s*docs_dir\s*:\s*(.+)$""")
    private val themeKeyRegex = Regex("""^(?:theme|["']theme["'])\s*:""")
    private val fallbackThemeConfigFileName = ".authord.theme.yml"

    /**
     * Activates plugin runtime for a project.
     *
     * @param projectId stable project key.
     * @param projectPath project root path.
     * @param startupOutput retained for source compatibility; startup now uses HTTP readiness probes.
     * @param featureFlags effective feature-flag policy.
     */
    fun activate(
        projectId: String,
        projectPath: String,
        @Suppress("UNUSED_PARAMETER") startupOutput: String,
        featureFlags: FeatureFlagPolicy,
        strictPreflightOnChange: Boolean = false,
        allowDependencyBypass: Boolean = false,
        retryDependencySetup: Boolean = false,
    ): ActivationResult {
        if (!featureFlags.allowsMvpFlow() || !featureFlags.disallowsFutureCycleFeatures()) {
            val reason = ActivationFailureReason.MVP_DISABLED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        if (isMaterializedProjectRoot(projectPath) && resolveConfigPath(projectPath) == null) {
            val reason = ActivationFailureReason.START_FAILED
            val details = AuthordUiBundle.message("activation.error.configNotFound", projectPath)
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, details),
            )
        }

        if (retryDependencySetup) {
            bootstrapService.requestDependencyRetry(projectPath)
        }
        if (strictPreflightOnChange) {
            bootstrapService.requestStrictVerification(projectPath)
        }
        if (allowDependencyBypass) {
            bootstrapService.requestStartAnyway(projectPath)
        }

        val bootstrapResult = bootstrapService.bootstrap(projectPath)
        logBootstrapDiagnostics(projectPath, bootstrapResult.diagnostics)
        if (!bootstrapResult.success) {
            val reason = ActivationFailureReason.BOOTSTRAP_FAILED
            val initialClassification = classifyStartupFailure(
                rawMessage = bootstrapResult.errorMessage,
                pythonExecutable = bootstrapResult.diagnostics.pythonExecutable,
                dependencyDeclarationHint = "",
            )
            val dependencyHint = dependencyDeclarationHint(
                projectPath = projectPath,
                suggestedPackage = initialClassification.installPackage,
            )
            val classified = classifyStartupFailure(
                rawMessage = bootstrapResult.errorMessage,
                pythonExecutable = bootstrapResult.diagnostics.pythonExecutable,
                dependencyDeclarationHint = dependencyHint,
            )
            val actionableDetails = buildActionableFailureMessage(classified)
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, actionableDetails),
                diagnostics = buildActivationDiagnostics(
                    bootstrapDiagnostics = bootstrapResult.diagnostics,
                    classifiedFailure = classified,
                    dependencyHint = dependencyHint,
                ),
                startAnywayAvailable = bootstrapResult.canStartAnyway,
            )
        }

        val attempts = maxStartupAttempts.coerceAtLeast(1)
        val host = loopbackHostAddress()
        var lastFailure: StartupAttemptFailure? = null

        for (attempt in 1..attempts) {
            val port = allocateLoopbackPort()
            if (port == null) {
                lastFailure = StartupAttemptFailure(
                    baseUrl = "",
                    failureSummary = "Could not allocate a free loopback port.",
                    diagnostics = processManager.diagnostics(projectId),
                    startupOutput = "",
                )
                safeSleep(startupPollIntervalMillis)
                continue
            }

            val baseUrl = "http://$host:$port/"
            LOG.info("Starting Authord preview runtime for $projectId at $baseUrl (attempt $attempt/$attempts)")

            val startResult = processManager.start(
                projectId = projectId,
                workingDir = projectPath,
                config = RuntimeServerConfig(
                    command = parentBoundServeCommand(
                        projectId = projectId,
                        projectPath = projectPath,
                        runtimePath = bootstrapResult.runtimePath,
                        uvExecutablePath = bootstrapResult.uvExecutablePath,
                        host = host,
                        port = port,
                    ),
                ),
            )

            if (!startResult.started) {
                val diagnostics = processManager.diagnostics(projectId)
                logWarnings(diagnostics?.startupOutput.orEmpty())
                lastFailure = StartupAttemptFailure(
                    baseUrl = baseUrl,
                    failureSummary = "Authord process failed to start.",
                    diagnostics = diagnostics,
                    startupOutput = startResult.startupOutput,
                )
                processManager.stop(projectId)
                safeSleep(startupPollIntervalMillis)
                continue
            }

            if (startResult.alreadyRunning) {
                val diagnostics = processManager.diagnostics(projectId)
                logWarnings(diagnostics?.startupOutput.orEmpty())
                lastFailure = StartupAttemptFailure(
                    baseUrl = baseUrl,
                    failureSummary = "Authord process was already running before startup attempt.",
                    diagnostics = diagnostics,
                    startupOutput = startResult.startupOutput,
                )
                processManager.stop(projectId)
                safeSleep(startupPollIntervalMillis)
                continue
            }

            when (val readiness = waitUntilUp(projectId, baseUrl)) {
                is StartupReadiness.Ready -> {
                    logWarnings(readiness.diagnostics?.startupOutput.orEmpty())
                    previewPaneCoordinator.open(projectId, baseUrl)
                    return ActivationResult(
                        success = true,
                        previewUrl = baseUrl,
                        message = AuthordUiBundle.message("activation.status.completed"),
                        diagnostics = buildActivationDiagnostics(
                            bootstrapDiagnostics = bootstrapResult.diagnostics,
                            classifiedFailure = null,
                            dependencyHint = "",
                        ),
                    )
                }

                is StartupReadiness.ProcessExited -> {
                    logWarnings(readiness.diagnostics?.startupOutput.orEmpty())
                    lastFailure = StartupAttemptFailure(
                        baseUrl = baseUrl,
                        failureSummary = "Authord process exited before readiness probe succeeded.",
                        diagnostics = readiness.diagnostics,
                        startupOutput = startResult.startupOutput,
                    )
                }

                is StartupReadiness.TimedOut -> {
                    logWarnings(readiness.diagnostics?.startupOutput.orEmpty())
                    lastFailure = StartupAttemptFailure(
                        baseUrl = baseUrl,
                        failureSummary = "Authord readiness probe timed out.",
                        diagnostics = readiness.diagnostics,
                        startupOutput = startResult.startupOutput,
                    )
                }
            }

            processManager.stop(projectId)
            safeSleep(startupPollIntervalMillis)
        }

        val reason = ActivationFailureReason.START_FAILED
        val failureDetails = startupFailureDetails(
            projectPath = projectPath,
            failure = lastFailure,
            bootstrapDiagnostics = bootstrapResult.diagnostics,
        )
        bootstrapService.requestStrictVerification(projectPath)
        return ActivationResult(
            success = false,
            reason = reason,
            message = errorPresenter.present(reason, failureDetails.details),
            diagnostics = buildActivationDiagnostics(
                bootstrapDiagnostics = bootstrapResult.diagnostics,
                classifiedFailure = failureDetails.classifiedFailure,
                dependencyHint = failureDetails.classifiedFailure.dependencyDeclarationHint.orEmpty(),
            ),
            startAnywayAvailable = false,
        )
    }

    private fun waitUntilUp(projectId: String, baseUrl: String): StartupReadiness {
        val pollInterval = startupPollIntervalMillis.coerceIn(100L, 250L)
        val timeout = startupProbeTimeoutMillis.coerceAtLeast(1_000L)
        val deadline = nowMillisProvider() + timeout
        var latestDiagnostics = processManager.diagnostics(projectId)

        while (nowMillisProvider() <= deadline) {
            if (readinessProbe.isReady(baseUrl)) {
                return StartupReadiness.Ready(processManager.diagnostics(projectId) ?: latestDiagnostics)
            }

            latestDiagnostics = processManager.diagnostics(projectId)
            if (latestDiagnostics != null && !latestDiagnostics.isAlive) {
                return StartupReadiness.ProcessExited(latestDiagnostics)
            }

            if (!safeSleep(pollInterval)) {
                break
            }
        }

        return StartupReadiness.TimedOut(processManager.diagnostics(projectId) ?: latestDiagnostics)
    }

    private fun startupFailureDetails(
        projectPath: String,
        failure: StartupAttemptFailure?,
        bootstrapDiagnostics: BootstrapDiagnostics,
    ): ClassifiedStartupFailureDetails {
        if (failure == null) {
            val rawOutput = startFailureDetails(projectPath, "")
            val classified = classifyStartupFailureWithProjectHint(
                projectPath = projectPath,
                rawMessage = rawOutput,
                pythonExecutable = bootstrapDiagnostics.pythonExecutable,
            )
            val details = buildString {
                append(buildActionableFailureMessage(classified))
                append("\n")
                append(compactDiagnosticsSection(bootstrapDiagnostics, classified))
            }
            return ClassifiedStartupFailureDetails(
                details = details,
                rawOutput = rawOutput,
                classifiedFailure = classified,
            )
        }

        val diagnostics = failure.diagnostics
        val mergedOutput = listOfNotNull(
            diagnostics?.stdoutOutput,
            diagnostics?.stderrOutput,
            diagnostics?.startupOutput,
            failure.startupOutput,
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val parsedOutput = startFailureDetails(projectPath, mergedOutput)
        val classified = classifyStartupFailureWithProjectHint(
            projectPath = projectPath,
            rawMessage = parsedOutput.ifBlank { failure.failureSummary },
            pythonExecutable = bootstrapDiagnostics.pythonExecutable,
        )
        val stdoutTail = tailText(diagnostics?.stdoutOutput.orEmpty())
        val stderrTail = tailText(diagnostics?.stderrOutput.orEmpty())

        val details = buildString {
            append(failure.failureSummary)
            append(" Last attempted URL: ")
            append(failure.baseUrl.ifBlank { "<unavailable>" })
            append(". Process exit code: ")
            append(diagnostics?.exitCode?.toString() ?: "unavailable")
            append(". ")
            append(buildActionableFailureMessage(classified))

            if (stdoutTail.isNotBlank()) {
                append(". stdout tail:\n")
                append(stdoutTail)
            }

            if (stderrTail.isNotBlank()) {
                append("\nstderr tail:\n")
                append(stderrTail)
            }

            append("\n")
            append(compactDiagnosticsSection(bootstrapDiagnostics, classified))
        }

        return ClassifiedStartupFailureDetails(
            details = details,
            rawOutput = parsedOutput,
            classifiedFailure = classified,
        )
    }

    private fun tailText(text: String, maxLines: Int = 40, maxChars: Int = 4_000): String {
        if (text.isBlank()) {
            return ""
        }

        val tailLines = text
            .lineSequence()
            .toList()
            .takeLast(maxLines)
            .joinToString("\n")

        return if (tailLines.length <= maxChars) {
            tailLines
        } else {
            tailLines.takeLast(maxChars)
        }
    }

    private fun logWarnings(output: String) {
        output.lineSequence()
            .map(String::trim)
            .filter { it.contains("warning", ignoreCase = true) }
            .take(5)
            .forEach { warningLine ->
                LOG.info("Authord startup warning (non-fatal): $warningLine")
            }
    }

    private fun safeSleep(millis: Long): Boolean {
        if (millis <= 0L) {
            return true
        }

        return try {
            sleeper(millis)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun startFailureDetails(projectPath: String, startupOutput: String): String {
        val normalizedOutput = startupOutput.trim()
        if ((normalizedOutput.contains("InvalidGitRepositoryError") || normalizedOutput.contains("GitCommandError")) &&
            normalizedOutput.contains("mkdocs_git_revision_date_localized_plugin")
        ) {
            return AuthordUiBundle.message("activation.error.gitRequired")
        }

        if (normalizedOutput.contains("ModuleNotFoundError") ||
            normalizedOutput.contains("Theme '.*' is not installed".toRegex())
        ) {
            return AuthordUiBundle.message("activation.error.moduleMissing", normalizedOutput)
        }

        if (normalizedOutput.contains("yaml.scanner.ScannerError") ||
            normalizedOutput.contains("ConfigurationError")
        ) {
            return AuthordUiBundle.message("activation.error.yamlSyntax", normalizedOutput)
        }

        if (normalizedOutput.isNotBlank()) {
            return normalizedOutput
        }

        val rootPath = Path.of(projectPath)
        val hasConfigFile = rootPath.resolve("mkdocs.yml").exists() || rootPath.resolve("mkdocs.yaml").exists()
        if (!hasConfigFile) {
            return AuthordUiBundle.message("activation.error.configNotFound", projectPath)
        }

        return ""
    }

    private fun classifyStartupFailureWithProjectHint(
        projectPath: String,
        rawMessage: String,
        pythonExecutable: String,
    ): PreviewStartupFailure {
        val initial = classifyStartupFailure(
            rawMessage = rawMessage,
            pythonExecutable = pythonExecutable,
            dependencyDeclarationHint = "",
        )
        val dependencyHint = dependencyDeclarationHint(
            projectPath = projectPath,
            suggestedPackage = initial.installPackage,
        )
        return classifyStartupFailure(
            rawMessage = rawMessage,
            pythonExecutable = pythonExecutable,
            dependencyDeclarationHint = dependencyHint,
        )
    }

    private fun classifyStartupFailure(
        rawMessage: String,
        pythonExecutable: String,
        dependencyDeclarationHint: String,
    ): PreviewStartupFailure {
        return PreviewStartupFailureClassifier.classify(
            rawMessage = rawMessage.ifBlank { "Preview start failed." },
            context = PreviewStartupFailureContext(
                pythonExecutable = pythonExecutable.ifBlank { null },
                dependencyDeclarationHint = dependencyDeclarationHint.ifBlank { null },
            ),
        )
    }

    private fun buildActionableFailureMessage(classifiedFailure: PreviewStartupFailure): String {
        return buildString {
            append("Reason: ")
            append(classifiedFailure.reason)
            if (!classifiedFailure.configContext.isNullOrBlank()) {
                append(" ")
                append(classifiedFailure.configContext)
            }
            if (classifiedFailure.nextStep.isNotBlank()) {
                append(" Next step: ")
                append(classifiedFailure.nextStep)
            }
            if (classifiedFailure.persistenceGuidance.isNotBlank()) {
                append(" ")
                append(classifiedFailure.persistenceGuidance)
            }
            if (!classifiedFailure.dependencyDeclarationHint.isNullOrBlank()) {
                append(" ")
                append(classifiedFailure.dependencyDeclarationHint)
            }
        }
    }

    private fun compactDiagnosticsSection(
        bootstrapDiagnostics: BootstrapDiagnostics,
        classifiedFailure: PreviewStartupFailure,
    ): String {
        val bootstrapSummary = "get-deps=${bootstrapDiagnostics.getDepsOutcome.name.lowercase()}, fallback=${bootstrapDiagnostics.fallbackDependencySource.ifBlank { "none" }}"
        val verificationSummary = if (bootstrapDiagnostics.verificationMode == com.authord.mkdocs.runtime.BootstrapVerificationMode.NONE) {
            "none"
        } else {
            val status = if (bootstrapDiagnostics.verificationSucceeded == true) "ok" else "failed"
            "${bootstrapDiagnostics.verificationMode.name.lowercase()}:$status"
        }
        return buildString {
            append("Diagnostics:\n")
            append("- interpreter: ")
            append(bootstrapDiagnostics.pythonExecutable.ifBlank { "<unavailable>" })
            append('\n')
            append("- python: ")
            append(bootstrapDiagnostics.pythonVersion.ifBlank { "<unknown>" })
            append('\n')
            append("- mkdocs: ")
            append(bootstrapDiagnostics.mkdocsVersion.ifBlank { "<unknown>" })
            append('\n')
            append("- bootstrap: ")
            append(bootstrapSummary)
            append('\n')
            append("- verification: ")
            append(verificationSummary)
            append('\n')
            append("- classified: ")
            append(classifiedFailure.category.name.lowercase())
            if (classifiedFailure.installPackage != null) {
                append(" (")
                append(classifiedFailure.installPackage)
                append(")")
            }
            if (!classifiedFailure.dependencyDeclarationHint.isNullOrBlank()) {
                append('\n')
                append("- declared dependency hint: ")
                append(classifiedFailure.dependencyDeclarationHint)
            }
        }
    }

    private fun buildActivationDiagnostics(
        bootstrapDiagnostics: BootstrapDiagnostics,
        classifiedFailure: PreviewStartupFailure?,
        dependencyHint: String,
    ): ActivationDiagnostics {
        val bootstrapSummary = "get-deps=${bootstrapDiagnostics.getDepsOutcome.name.lowercase()}, fallback=${bootstrapDiagnostics.fallbackDependencySource.ifBlank { "none" }}"
        val verificationSummary = if (bootstrapDiagnostics.verificationMode == com.authord.mkdocs.runtime.BootstrapVerificationMode.NONE) {
            "none"
        } else {
            val status = if (bootstrapDiagnostics.verificationSucceeded == true) "ok" else "failed"
            "${bootstrapDiagnostics.verificationMode.name.lowercase()}:$status"
        }
        return ActivationDiagnostics(
            pythonExecutable = bootstrapDiagnostics.pythonExecutable,
            pythonVersion = bootstrapDiagnostics.pythonVersion,
            mkdocsVersion = bootstrapDiagnostics.mkdocsVersion,
            bootstrapResultSummary = bootstrapSummary,
            verificationSummary = verificationSummary,
            classifiedErrorCategory = classifiedFailure?.category?.name.orEmpty(),
            suggestedPackage = classifiedFailure?.installPackage.orEmpty(),
            dependencyDeclarationHint = dependencyHint,
        )
    }

    private fun dependencyDeclarationHint(projectPath: String, suggestedPackage: String?): String {
        if (suggestedPackage.isNullOrBlank()) {
            return ""
        }
        val declared = isDependencyDeclared(projectPath, suggestedPackage)
        return if (declared) {
            "Dependency appears declared in project dependency files; this may indicate interpreter/environment mismatch."
        } else {
            "Dependency does not appear in requirements.txt / requirements-docs.txt / pyproject.toml docs dependencies."
        }
    }

    private fun isDependencyDeclared(projectPath: String, suggestedPackage: String): Boolean {
        val normalized = normalizeDependencyToken(suggestedPackage)
        val alternate = normalized.replace('-', '_')
        return dependencyDeclarationFiles(projectPath).any { candidate ->
            val content = runCatching { Files.readString(candidate).lowercase() }.getOrDefault("")
            content.contains(normalized) || content.contains(alternate)
        }
    }

    private fun normalizeDependencyToken(token: String): String {
        return token.trim().lowercase().replace('_', '-')
    }

    private fun dependencyDeclarationFiles(projectPath: String): List<Path> {
        val root = runCatching { Path.of(projectPath) }.getOrNull() ?: return emptyList()
        if (!root.exists()) {
            return emptyList()
        }

        val files = mutableListOf<Path>()
        runCatching {
            Files.newDirectoryStream(root).use { entries ->
                for (entry in entries) {
                    val fileName = entry.fileName.toString()
                    if (
                        Files.isRegularFile(entry) &&
                        fileName.startsWith("requirements", ignoreCase = true) &&
                        fileName.endsWith(".txt", ignoreCase = true)
                    ) {
                        files.add(entry)
                    }
                }
            }
        }

        val pyproject = root.resolve("pyproject.toml")
        if (pyproject.exists() && Files.isRegularFile(pyproject)) {
            files.add(pyproject)
        }
        return files.distinct().sortedBy { it.toString() }
    }

    private fun logBootstrapDiagnostics(projectPath: String, diagnostics: BootstrapDiagnostics) {
        LOG.info(
            "Authord bootstrap diagnostics for $projectPath: python=${diagnostics.pythonExecutable}," +
                " py=${diagnostics.pythonVersion}, mkdocs=${diagnostics.mkdocsVersion}," +
                " getDeps=${diagnostics.getDepsOutcome}, verification=${diagnostics.verificationMode}" +
                "(${diagnostics.verificationSucceeded}), fallback=${diagnostics.fallbackDependencySource}",
        )
    }

    private fun isMaterializedProjectRoot(projectPath: String): Boolean {
        val rootPath = runCatching { Path.of(projectPath) }.getOrNull() ?: return false
        return rootPath.exists() && Files.isDirectory(rootPath)
    }

    private fun parentBoundServeCommand(
        projectId: String,
        projectPath: String,
        runtimePath: String,
        uvExecutablePath: String,
        host: String,
        port: Int,
    ): List<String> {
        ensureSiteNameRequiredByConfig(projectPath)
        val scriptPath = ensureParentGuardScript(projectPath)
        val parentPid = ProcessHandle.current().pid().toString()
        val fallbackThemeConfigPath = ensureFallbackThemeConfig(projectId, projectPath)
        val fallbackThemeConfigArgs = if (fallbackThemeConfigPath != null) {
            listOf("-f", fallbackThemeConfigPath.toString())
        } else {
            emptyList()
        }
        val hostBindingArgs = listOf("--dev-addr", "$host:$port")
        val runtimePythonExecutable = resolveRuntimePythonExecutable(runtimePath)

        return listOf(
            uvExecutablePath,
            "run",
            "--python",
            runtimePath,
            "python",
            scriptPath.toString(),
            "--parent-pid",
            parentPid,
            "--working-dir",
            projectPath,
            "--",
            runtimePythonExecutable,
            "-m",
            "mkdocs",
            "serve",
        ) + hostBindingArgs + fallbackThemeConfigArgs + listOf(
            "--livereload",
            "--dirty",
        )
    }

    private fun resolveRuntimePythonExecutable(runtimePath: String): String {
        val runtimeRoot = Path.of(runtimePath)
        val windowsPython = runtimeRoot.resolve("Scripts").resolve("python.exe")
        if (windowsPython.exists()) {
            return windowsPython.toString()
        }

        val unixPython = runtimeRoot.resolve("bin").resolve("python")
        if (unixPython.exists()) {
            return unixPython.toString()
        }

        return if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            windowsPython.toString()
        } else {
            unixPython.toString()
        }
    }

    private fun ensureFallbackThemeConfig(projectId: String, projectPath: String): Path? {
        if (!shouldUseDefaultThemeOverrides(projectPath)) {
            return null
        }

        val baseConfigPath = resolveConfigPath(projectPath) ?: return null
        val resolvedBaseConfigPath = baseConfigPath.toAbsolutePath().normalize().toString()
        val resolvedDocsDirPath = resolveDocsDirPath(projectPath, baseConfigPath).toAbsolutePath().normalize().toString()
        val fallbackThemeConfigPath = pluginScopedThemeConfigPath(projectId, projectPath)
        val fallbackColorMode = if (runCatching { isDarkIdeTheme() }.getOrDefault(false)) "dark" else "light"
        val fallbackConfig = buildString {
            append("INHERIT: '")
            append(escapeSingleQuotedYaml(resolvedBaseConfigPath))
            append("'\n")
            append("docs_dir: '")
            append(escapeSingleQuotedYaml(resolvedDocsDirPath))
            append("'\n")
            append("theme:\n")
            append("  name: mkdocs\n")
            append("  color_mode: $fallbackColorMode\n")
            append("  user_color_mode_toggle: true\n")
        }

        val wroteFallbackConfig = runCatching {
            Files.createDirectories(fallbackThemeConfigPath.parent)
            Files.writeString(
                fallbackThemeConfigPath,
                fallbackConfig,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }.isSuccess

        return if (wroteFallbackConfig) fallbackThemeConfigPath else null
    }

    private fun pluginScopedThemeConfigPath(projectId: String, projectPath: String): Path {
        val normalizedProjectPath = runCatching { Path.of(projectPath).toAbsolutePath().normalize().toString() }
            .getOrDefault(projectPath)
        val pathFingerprint = normalizedProjectPath.hashCode().toUInt().toString(16)
        val safeProjectId = projectId
            .ifBlank { "default" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return pluginEnvironmentRootProvider()
            .resolve("theme")
            .resolve("${safeProjectId}_$pathFingerprint")
            .resolve(fallbackThemeConfigFileName)
    }

    private fun shouldUseDefaultThemeOverrides(projectPath: String): Boolean {
        val configPath = resolveConfigPath(projectPath) ?: return true
        val existing = runCatching { Files.readString(configPath) }.getOrNull() ?: return true
        return existing.lineSequence().none { line ->
            val trimmed = line.trimStart()
            trimmed.isNotEmpty() &&
                !trimmed.startsWith("#") &&
                themeKeyRegex.containsMatchIn(trimmed)
        }
    }

    private fun resolveDocsDirPath(projectPath: String, configPath: Path): Path {
        val configuredDocsDir = runCatching { Files.readString(configPath) }
            .getOrNull()
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.mapNotNull { line ->
                val match = docsDirKeyRegex.find(line) ?: return@mapNotNull null
                parseYamlScalar(match.groupValues[1])
            }
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "docs"

        val configuredPath = runCatching { Path.of(configuredDocsDir) }.getOrNull()
        return if (configuredPath != null && configuredPath.isAbsolute) {
            configuredPath
        } else {
            Path.of(projectPath).resolve(configuredDocsDir)
        }
    }

    private fun parseYamlScalar(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.length >= 2 && trimmed.startsWith('\'') && trimmed.endsWith('\'')) {
            return trimmed.substring(1, trimmed.length - 1).replace("''", "'")
        }
        if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            return trimmed.substring(1, trimmed.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return trimmed.substringBefore('#').trim()
    }

    private fun ensureSiteNameRequiredByConfig(projectPath: String) {
        val configPath = resolveConfigPath(projectPath) ?: return
        val existing = runCatching { Files.readString(configPath) }.getOrNull() ?: return
        if (existing.lineSequence().any { line ->
                val trimmed = line.trimStart()
                trimmed.isNotEmpty() && !trimmed.startsWith("#") && siteNameKeyRegex.containsMatchIn(trimmed)
            }
        ) {
            return
        }

        val fallbackSiteName = defaultSiteName(Path.of(projectPath))
        val addition = buildString {
            if (!existing.endsWith('\n')) {
                append('\n')
            }
            append("site_name: '${escapeSingleQuotedYaml(fallbackSiteName)}'\n")
        }
        runCatching {
            Files.writeString(configPath, addition, StandardOpenOption.APPEND)
        }
    }

    private fun resolveConfigPath(projectPath: String): Path? {
        val rootPath = Path.of(projectPath)
        val yml = rootPath.resolve("mkdocs.yml")
        if (yml.exists()) {
            return yml
        }
        val yaml = rootPath.resolve("mkdocs.yaml")
        if (yaml.exists()) {
            return yaml
        }
        return null
    }

    private fun defaultSiteName(projectPath: Path): String {
        val defaultSiteName = AuthordUiBundle.message("activation.default.siteName")
        val leafName = projectPath.name.trim()
        if (leafName.isBlank()) {
            return defaultSiteName
        }
        return leafName
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .ifBlank { defaultSiteName }
    }

    private fun escapeSingleQuotedYaml(value: String): String = value.replace("'", "''")

    private fun allocateLoopbackPort(): Int? {
        return runCatching {
            ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { socket ->
                socket.reuseAddress = true
                socket.localPort.takeIf { it > 0 }
            }
        }.getOrNull()
    }

    private fun loopbackHostAddress(): String {
        val resolved = runCatching { InetAddress.getLoopbackAddress().hostAddress }.getOrDefault("127.0.0.1")
        if (resolved.isBlank() || resolved.contains(':')) {
            return "127.0.0.1"
        }
        return resolved
    }

    private fun pluginRuntimeDir(projectPath: String): Path = Path.of(projectPath).resolve(".mkdocs-plugin-runtime")

    private fun ensureParentGuardScript(projectPath: String): Path {
        val runtimeDir = pluginRuntimeDir(projectPath)
        val scriptPath = runtimeDir.resolve("serve_with_parent_guard.py")
        val script = """
            import argparse
            import errno
            import os
            import subprocess
            import sys
            import threading
            import time
            
            def parent_alive(parent_pid: int) -> bool:
                if parent_pid <= 0:
                    return False
                try:
                    os.kill(parent_pid, 0)
                    return True
                except OSError as exc:
                    if exc.errno in (errno.ESRCH, errno.EINVAL):
                        return False
                    if exc.errno == errno.EPERM:
                        return True
                    return False
            
            def terminate_process(proc: subprocess.Popen[str]) -> None:
                if proc.poll() is not None:
                    return
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
            
            def stream_output(proc: subprocess.Popen[str]) -> None:
                assert proc.stdout is not None
                for line in proc.stdout:
                    sys.stdout.write(line)
                    sys.stdout.flush()
            
            def main() -> int:
                parser = argparse.ArgumentParser()
                parser.add_argument("--parent-pid", type=int, required=True)
                parser.add_argument("--working-dir", required=True)
                parser.add_argument("command", nargs=argparse.REMAINDER)
                args = parser.parse_args()
            
                command = args.command
                if command and command[0] == "--":
                    command = command[1:]
                if not command:
                    print("No command supplied for guarded execution.", file=sys.stderr)
                    return 2
            
                process = subprocess.Popen(
                    command,
                    cwd=args.working_dir,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    bufsize=1,
                )
            
                reader = threading.Thread(target=stream_output, args=(process,), daemon=True)
                reader.start()
            
                while True:
                    exit_code = process.poll()
                    if exit_code is not None:
                        return exit_code
                    if not parent_alive(args.parent_pid):
                        terminate_process(process)
                        return 0
                    time.sleep(0.5)
            
            if __name__ == "__main__":
                sys.exit(main())
        """.trimIndent() + "\n"

        Files.createDirectories(runtimeDir)
        Files.writeString(
            scriptPath,
            script,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
        return scriptPath
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(PluginActivationService::class.java)
    }
}
