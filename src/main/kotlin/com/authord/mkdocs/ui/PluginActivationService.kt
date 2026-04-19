package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy
import com.authord.mkdocs.runtime.BaseUrlDetector
import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.authord.mkdocs.runtime.RuntimeProcessDiagnostics
import com.authord.mkdocs.runtime.RuntimeServerConfig
import com.authord.mkdocs.runtime.UvBootstrapService
import com.authord.mkdocs.ui.intellij.AuthordUiBundle
import com.authord.mkdocs.ui.intellij.SiteContext
import com.authord.mkdocs.ui.intellij.SiteContextResolver
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import java.io.File
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
        } catch (exception: Exception) {
            if (exception is InterruptedException || exception is java.io.InterruptedIOException) {
                Thread.currentThread().interrupt()
            } else if (exception !is java.io.IOException) {
                LOG.debug("Authord readiness probe failed for baseUrl=$baseUrl", exception)
            }
            false
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        private val LOG: Logger = Logger.getInstance(HttpURLConnectionReadinessProbe::class.java)
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
data class ActivationResult(
    val success: Boolean,
    val reason: ActivationFailureReason? = null,
    val message: String = "",
    val previewUrl: String = "",
)

/**
 * Orchestrates adopt-first runtime startup and preview opening.
 */
class PluginActivationService(
    private val bootstrapService: UvBootstrapService,
    private val processManager: MkdocsProcessManager,
    private val baseUrlDetector: BaseUrlDetector,
    private val previewPaneCoordinator: PreviewPaneCoordinator,
    private val errorPresenter: ActivationErrorPresenter,
    private val isDarkIdeTheme: () -> Boolean = { false },
    private val readinessProbe: HttpReadinessProbe = HttpURLConnectionReadinessProbe(),
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { millis -> Thread.sleep(millis) },
    private val maxStartupAttempts: Int = 3,
    private val startupProbeTimeoutMillis: Long = 45_000L,
    private val startupPollIntervalMillis: Long = 150L,
    private val pluginEnvironmentRootProvider: () -> Path = ::defaultPluginEnvironmentRoot,
) {
    private enum class RuntimeCandidateKind {
        AUTHORD_VENV,
        LOCAL_VENV,
        NON_LOCAL,
    }

    private data class RuntimeCommandCandidate(
        val displayName: String,
        val commandPrefix: List<String>,
        val kind: RuntimeCandidateKind,
        val siteRoot: Path? = null,
    )

    private data class StartupAttemptFailure(
        val baseUrl: String,
        val failureSummary: String,
        val diagnostics: RuntimeProcessDiagnostics?,
        val startupOutput: String,
    )

    private data class DependencyInstallGuidance(
        val reason: String,
        val installPackage: String,
        val suggestedCommand: String,
        val confidenceScore: Int,
        val heuristicPackage: Boolean = false,
    )

    private sealed interface RuntimeStartupOutcome {
        data class Started(val baseUrl: String) : RuntimeStartupOutcome

        data class CommandUnavailable(val details: String) : RuntimeStartupOutcome

        data class RecoverableDependencyFailure(
            val failureDetails: String,
            val installPackage: String,
            val suggestedCommand: String,
            val confidenceScore: Int,
        ) : RuntimeStartupOutcome

        data class Failed(val failureDetails: String) : RuntimeStartupOutcome
    }

    private sealed interface StartupReadiness {
        data class Ready(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class ProcessExited(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class TimedOut(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness
    }

    private val siteNameKeyRegex = Regex("""^\s*site_name\s*:""")
    private val themeKeyRegex = Regex("""^(?:theme|["']theme["'])\s*:""")
    private val themeNotInstalledRegex = Regex("""Theme ['"]([^'"]+)['"] is not installed""", RegexOption.IGNORE_CASE)
    private val themeUnrecognizedRegex = Regex("""Unrecogni[sz]ed theme name:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val moduleMissingRegex = Regex("""No module named ['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
    private val pluginMissingRegex = Regex(
        """(?:Config value ['"]plugins['"]:\s*)?(?:The\s+)?['"]([^'"]+)['"]\s+plugin\s+is\s+not\s+installed""",
        RegexOption.IGNORE_CASE,
    )
    private val knownModulePackageMap = mapOf(
        "material" to "mkdocs-material",
        "pymdownx" to "pymdown-extensions",
    )
    private val builtInThemeIds = setOf("mkdocs", "readthedocs")
    private val fallbackThemeConfigFileName = ".authord.theme.yml"
    private val mkdocsDevAddrFlag = "--" + "dev-addr"
    private val authordRuntimeVenvDirectoryName = ".authord_venv"
    private val projectVenvDirectoryName = ".venv"
    private val siteContextResolver = SiteContextResolver()

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
    ): ActivationResult {
        if (!featureFlags.allowsMvpFlow() || !featureFlags.disallowsFutureCycleFeatures()) {
            val reason = ActivationFailureReason.MVP_DISABLED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason),
            )
        }

        val siteContext = resolveSiteContext(projectPath)
        if (isMaterializedProjectRoot(projectPath) && siteContext == null) {
            val reason = ActivationFailureReason.START_FAILED
            val details = buildSetupGuidanceForMissingConfig(projectPath)
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, details),
            )
        }

        val runtimeCandidates = resolveRuntimeCandidates(projectPath, siteContext)
        val unavailableCandidates = mutableListOf<String>()
        val startupFailures = mutableListOf<String>()
        val dependencyFailures = mutableListOf<RuntimeStartupOutcome.RecoverableDependencyFailure>()
        var sawNonLocalCandidateFailure = false

        runtimeCandidates.forEach { candidate ->
            when (
                val outcome = startRuntimeCandidate(
                    projectId = projectId,
                    projectPath = projectPath,
                    siteContext = siteContext,
                    candidate = candidate,
                )
            ) {
                is RuntimeStartupOutcome.Started -> {
                    previewPaneCoordinator.open(projectId, outcome.baseUrl)
                    return ActivationResult(
                        success = true,
                        previewUrl = outcome.baseUrl,
                        message = AuthordUiBundle.message("activation.status.completed"),
                    )
                }

                is RuntimeStartupOutcome.CommandUnavailable -> {
                    unavailableCandidates += candidate.displayName
                    if (candidate.kind == RuntimeCandidateKind.NON_LOCAL) {
                        sawNonLocalCandidateFailure = true
                    }
                    LOG.info(
                        "Authord runtime candidate '${candidate.displayName}' was not runnable for projectId=$projectId: ${outcome.details}",
                    )
                }

                is RuntimeStartupOutcome.RecoverableDependencyFailure -> {
                    dependencyFailures += outcome
                    if (candidate.kind == RuntimeCandidateKind.NON_LOCAL) {
                        sawNonLocalCandidateFailure = true
                    }
                    LOG.info(
                        "Authord runtime candidate '${candidate.displayName}' failed with recoverable dependency issue for projectId=$projectId",
                    )
                }

                is RuntimeStartupOutcome.Failed -> {
                    startupFailures += outcome.failureDetails
                    if (candidate.kind == RuntimeCandidateKind.NON_LOCAL) {
                        sawNonLocalCandidateFailure = true
                    }
                    LOG.info(
                        "Authord runtime candidate '${candidate.displayName}' failed during startup for projectId=$projectId",
                    )
                }
            }
        }

        if (siteContext != null && sawNonLocalCandidateFailure) {
            when (
                val forcedOutcome = runForcedAuthordFallback(
                    projectId = projectId,
                    projectPath = projectPath,
                    siteContext = siteContext,
                )
            ) {
                is RuntimeStartupOutcome.Started -> {
                    previewPaneCoordinator.open(projectId, forcedOutcome.baseUrl)
                    return ActivationResult(
                        success = true,
                        previewUrl = forcedOutcome.baseUrl,
                        message = AuthordUiBundle.message("activation.status.completed"),
                    )
                }

                is RuntimeStartupOutcome.CommandUnavailable -> {
                    unavailableCandidates += "$authordRuntimeVenvDirectoryName (forced fallback)"
                }

                is RuntimeStartupOutcome.RecoverableDependencyFailure -> {
                    dependencyFailures += forcedOutcome
                }

                is RuntimeStartupOutcome.Failed -> {
                    startupFailures += forcedOutcome.failureDetails
                }
            }
        }

        if (dependencyFailures.isNotEmpty()) {
            val reason = ActivationFailureReason.START_FAILED
            val latest = dependencyFailures.last()
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, latest.failureDetails),
            )
        }

        if (startupFailures.isNotEmpty()) {
            val reason = ActivationFailureReason.START_FAILED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, startupFailures.last()),
            )
        }

        return ActivationResult(
            success = false,
            reason = ActivationFailureReason.START_FAILED,
            message = noRunnableRuntimeMessage(unavailableCandidates),
        )
    }

    private fun startRuntimeCandidate(
        projectId: String,
        projectPath: String,
        siteContext: SiteContext?,
        candidate: RuntimeCommandCandidate,
        syncAuthordDependencies: Boolean = true,
    ): RuntimeStartupOutcome {
        if (syncAuthordDependencies) {
            syncAuthordRuntimeDependencies(siteContext, candidate)?.let { syncFailure ->
                return syncFailure
            }
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
            LOG.info(
                "Starting Authord preview runtime for $projectId using '${candidate.displayName}' at $baseUrl (attempt $attempt/$attempts)",
            )
            val command = buildServeCommand(
                projectId = projectId,
                projectPath = projectPath,
                siteContext = siteContext,
                commandPrefix = candidate.commandPrefix,
                host = host,
                port = port,
            )
            val startResult = try {
                processManager.start(
                    projectId = projectId,
                    workingDir = projectPath,
                    config = RuntimeServerConfig(command = command),
                )
            } catch (error: Exception) {
                val details = error.message.orEmpty()
                if (looksLikeCommandUnavailable(details)) {
                    return RuntimeStartupOutcome.CommandUnavailable(details.ifBlank { "Command not found" })
                }
                lastFailure = StartupAttemptFailure(
                    baseUrl = baseUrl,
                    failureSummary = "Authord process launch failed: ${details.ifBlank { error::class.simpleName.orEmpty() }}",
                    diagnostics = processManager.diagnostics(projectId),
                    startupOutput = "",
                )
                safeSleep(startupPollIntervalMillis)
                continue
            }

            if (!startResult.started) {
                val diagnostics = processManager.diagnostics(projectId)
                logWarnings(diagnostics?.startupOutput.orEmpty())
                val mergedOutput = listOf(
                    startResult.startupOutput,
                    diagnostics?.stdoutOutput.orEmpty(),
                    diagnostics?.stderrOutput.orEmpty(),
                    diagnostics?.startupOutput.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString("\n")
                if (looksLikeCommandUnavailable(mergedOutput)) {
                    processManager.stop(projectId)
                    return RuntimeStartupOutcome.CommandUnavailable(mergedOutput)
                }
                detectRecoverableDependencyFailure(mergedOutput, candidate)?.let { dependencyFailure ->
                    processManager.stop(projectId)
                    return dependencyFailure
                }
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

            var shouldDelayBeforeRetry = true
            when (val readiness = waitUntilUp(projectId, baseUrl)) {
                is StartupReadiness.Ready -> {
                    logWarnings(readiness.diagnostics?.startupOutput.orEmpty())
                    val canonicalBaseUrl = resolveCanonicalPreviewBaseUrl(
                        expectedBaseUrl = baseUrl,
                        diagnostics = readiness.diagnostics,
                    )
                    if (canonicalBaseUrl != normalizeBaseUrl(baseUrl)) {
                        LOG.info(
                            "Authord runtime canonicalized preview base URL from '$baseUrl' to '$canonicalBaseUrl' for projectId=$projectId",
                        )
                    }
                    return RuntimeStartupOutcome.Started(canonicalBaseUrl)
                }

                is StartupReadiness.ProcessExited -> {
                    val output = mergeStartupOutput(readiness.diagnostics, startResult.startupOutput)
                    logWarnings(output)
                    if (looksLikeCommandUnavailable(output)) {
                        processManager.stop(projectId)
                        return RuntimeStartupOutcome.CommandUnavailable(output)
                    }
                    detectRecoverableDependencyFailure(output, candidate)?.let { dependencyFailure ->
                        processManager.stop(projectId)
                        return dependencyFailure
                    }
                    shouldDelayBeforeRetry = !hasPortBindConflict(output)
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
            if (shouldDelayBeforeRetry) {
                safeSleep(startupPollIntervalMillis)
            }
        }

        return RuntimeStartupOutcome.Failed(startupFailureDetails(projectPath, lastFailure))
    }

    private fun runForcedAuthordFallback(
        projectId: String,
        projectPath: String,
        siteContext: SiteContext,
    ): RuntimeStartupOutcome {
        val siteRoot = runCatching { siteContext.configPath.parent.toAbsolutePath().normalize() }.getOrNull()
            ?: return RuntimeStartupOutcome.Failed("Unable to resolve site root for forced Authord runtime fallback.")
        val runtimeRoot = siteRoot.resolve(authordRuntimeVenvDirectoryName)
        val fallbackCandidate = RuntimeCommandCandidate(
            displayName = "$authordRuntimeVenvDirectoryName (forced fallback)",
            commandPrefix = listOf(preferredAuthordMkdocsExecutable(runtimeRoot)),
            kind = RuntimeCandidateKind.AUTHORD_VENV,
            siteRoot = siteRoot,
        )
        val bootstrap = bootstrapService.bootstrapWithActiveConfig(
            projectPath = siteRoot.toString(),
            activeConfigPath = siteContext.configPath,
        )
        if (!bootstrap.success) {
            val bootstrapError = bootstrap.errorMessage.trim().ifBlank { "Unknown bootstrap failure." }
            detectRecoverableDependencyFailure(bootstrapError, fallbackCandidate)?.let { failure ->
                return appendDependencyFailureDetails(
                    failure,
                    "Authord runtime bootstrap failed at '$runtimeRoot'.",
                )
            }
            return RuntimeStartupOutcome.Failed(
                buildString {
                    append("Authord runtime bootstrap failed at '$runtimeRoot'. ")
                    append(bootstrapError)
                },
            )
        }

        val mkdocsExecutable = resolveVenvMkdocsExecutable(runtimeRoot)
            ?: return RuntimeStartupOutcome.Failed(
                "Authord runtime bootstrap completed but no runnable mkdocs executable was found at '$runtimeRoot'.",
            )

        val forcedCandidate = fallbackCandidate.copy(commandPrefix = listOf(mkdocsExecutable))
        return startRuntimeCandidate(
            projectId = projectId,
            projectPath = projectPath,
            siteContext = siteContext,
            candidate = forcedCandidate,
            syncAuthordDependencies = false,
        )
    }

    private fun syncAuthordRuntimeDependencies(
        siteContext: SiteContext?,
        candidate: RuntimeCommandCandidate,
    ): RuntimeStartupOutcome? {
        if (candidate.kind != RuntimeCandidateKind.AUTHORD_VENV) {
            return null
        }
        val activeConfigPath = siteContext?.configPath ?: return null
        val siteRoot = candidate.siteRoot
            ?: runCatching { activeConfigPath.parent.toAbsolutePath().normalize() }.getOrNull()
            ?: return RuntimeStartupOutcome.Failed("Unable to resolve site root for Authord runtime dependency sync.")
        val runtimeRoot = siteRoot.resolve(authordRuntimeVenvDirectoryName)
        val bootstrap = bootstrapService.bootstrapWithActiveConfig(
            projectPath = siteRoot.toString(),
            activeConfigPath = activeConfigPath,
        )
        if (bootstrap.success) {
            return null
        }
        val bootstrapError = bootstrap.errorMessage.trim().ifBlank { "Unknown bootstrap failure." }
        detectRecoverableDependencyFailure(bootstrapError, candidate)?.let { failure ->
            return appendDependencyFailureDetails(
                failure,
                "Authord runtime dependency sync failed at '$runtimeRoot'.",
            )
        }
        return RuntimeStartupOutcome.Failed(
            buildString {
                append("Authord runtime dependency sync failed at '$runtimeRoot'. ")
                append(bootstrapError)
            },
        )
    }

    private fun preferredAuthordMkdocsExecutable(runtimeRoot: Path): String {
        return resolveVenvMkdocsExecutable(runtimeRoot)
            ?: if (isWindows()) {
                runtimeRoot.resolve("Scripts").resolve("mkdocs.exe").toString()
            } else {
                runtimeRoot.resolve("bin").resolve("mkdocs").toString()
            }
    }

    private fun detectRecoverableDependencyFailure(
        startupOutput: String,
        candidate: RuntimeCommandCandidate,
    ): RuntimeStartupOutcome.RecoverableDependencyFailure? {
        if (startupOutput.isBlank()) {
            return null
        }
        val guidance = resolveDependencyInstallGuidance(startupOutput, candidate) ?: return null
        val details = buildString {
            append(guidance.reason)
            append("\nSuggested command: ")
            append(guidance.suggestedCommand)
            append("\nAfter install, retry Start Authord Preview (IDE restart not required).")
            if (guidance.heuristicPackage) {
                append("\nPackage suggestion is heuristic; adjust package name if needed for your environment.")
            }
        }
        return RuntimeStartupOutcome.RecoverableDependencyFailure(
            failureDetails = details,
            installPackage = guidance.installPackage,
            suggestedCommand = guidance.suggestedCommand,
            confidenceScore = guidance.confidenceScore,
        )
    }

    private fun appendDependencyFailureDetails(
        failure: RuntimeStartupOutcome.RecoverableDependencyFailure,
        extraDetails: String,
    ): RuntimeStartupOutcome.RecoverableDependencyFailure {
        val appended = extraDetails.trim()
        if (appended.isBlank()) {
            return failure
        }
        return failure.copy(
            failureDetails = buildString {
                append(failure.failureDetails.trim())
                append("\n")
                append(appended)
            },
        )
    }

    private fun resolveDependencyInstallGuidance(
        startupOutput: String,
        candidate: RuntimeCommandCandidate,
    ): DependencyInstallGuidance? {
        val normalized = startupOutput.trim()
        if (normalized.isBlank()) {
            return null
        }

        val unrecognizedTheme = themeUnrecognizedRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!unrecognizedTheme.isNullOrBlank()) {
            val packageResult = resolveThemePackage(unrecognizedTheme) ?: return null
            return DependencyInstallGuidance(
                reason = "MkDocs theme `$unrecognizedTheme` is configured but not installed in the preview runtime.",
                installPackage = packageResult.first,
                suggestedCommand = buildSuggestedInstallCommand(
                    candidate = candidate,
                    installPackage = packageResult.first,
                ),
                confidenceScore = packageResult.second,
                heuristicPackage = packageResult.third,
            )
        }

        val missingTheme = themeNotInstalledRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!missingTheme.isNullOrBlank()) {
            val packageResult = resolveThemePackage(missingTheme) ?: return null
            return DependencyInstallGuidance(
                reason = "MkDocs theme `$missingTheme` is configured but not installed in the preview runtime.",
                installPackage = packageResult.first,
                suggestedCommand = buildSuggestedInstallCommand(
                    candidate = candidate,
                    installPackage = packageResult.first,
                ),
                confidenceScore = packageResult.second,
                heuristicPackage = packageResult.third,
            )
        }

        val pluginMatch = pluginMissingRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!pluginMatch.isNullOrBlank()) {
            val normalizedPluginId = pluginMatch.lowercase().replace('_', '-')
            val inferredPackage = if (normalizedPluginId.startsWith("mkdocs-")) {
                normalizedPluginId
            } else {
                "mkdocs-$normalizedPluginId"
            }
            return DependencyInstallGuidance(
                reason = "MkDocs plugin `$normalizedPluginId` is declared in `mkdocs.yml` but is not installed in the preview runtime.",
                installPackage = inferredPackage,
                suggestedCommand = buildSuggestedInstallCommand(
                    candidate = candidate,
                    installPackage = inferredPackage,
                ),
                confidenceScore = 85,
                heuristicPackage = !normalizedPluginId.startsWith("mkdocs-"),
            )
        }

        val missingModule = moduleMissingRegex.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        if (!missingModule.isNullOrBlank()) {
            val inferredPackage = inferPackageForModule(missingModule) ?: return null
            val moduleRoot = missingModule.substringBefore('.')
            val heuristic = moduleRoot !in knownModulePackageMap
            return DependencyInstallGuidance(
                reason = "Missing Python dependency module: $missingModule.",
                installPackage = inferredPackage,
                suggestedCommand = buildSuggestedInstallCommand(
                    candidate = candidate,
                    installPackage = inferredPackage,
                ),
                confidenceScore = if (heuristic) 70 else 92,
                heuristicPackage = heuristic,
            )
        }

        return null
    }

    private fun resolveThemePackage(themeId: String): Triple<String, Int, Boolean>? {
        val normalizedTheme = themeId.trim().lowercase().replace('_', '-')
        if (normalizedTheme.isBlank() || normalizedTheme in builtInThemeIds) {
            return null
        }
        if (normalizedTheme == "material") {
            return Triple("mkdocs-material", 98, false)
        }
        val inferred = if (normalizedTheme.startsWith("mkdocs-")) normalizedTheme else "mkdocs-$normalizedTheme"
        return Triple(inferred, 78, !normalizedTheme.startsWith("mkdocs-"))
    }

    private fun inferPackageForModule(moduleName: String): String? {
        val normalized = moduleName.trim()
        if (normalized.isBlank()) {
            return null
        }
        val root = normalized.substringBefore('.').lowercase()
        knownModulePackageMap[root]?.let { return it }
        if (root.startsWith("mkdocs_")) {
            return "mkdocs-${root.removePrefix("mkdocs_").replace('_', '-')}"
        }
        if (root.startsWith("mkdocs-")) {
            return root
        }
        return root.replace('_', '-')
    }

    private fun buildSuggestedInstallCommand(
        candidate: RuntimeCommandCandidate,
        installPackage: String,
    ): String {
        val prefix = buildInstallCommandPrefix(candidate)
        return (prefix + installPackage).joinToString(" ")
    }

    private fun buildInstallCommandPrefix(candidate: RuntimeCommandCandidate): List<String> {
        if (candidate.kind == RuntimeCandidateKind.AUTHORD_VENV) {
            resolveAuthordVenvPython(candidate.siteRoot)?.let { python ->
                return listOf(python, "-m", "pip", "install")
            }
        }

        val commandPrefix = candidate.commandPrefix
        if (commandPrefix.isEmpty()) {
            return listOf("python", "-m", "pip", "install")
        }

        if (commandPrefix.size >= 3 &&
            commandPrefix[1].equals("run", ignoreCase = true) &&
            commandPrefix[2].equals("mkdocs", ignoreCase = true)
        ) {
            val executable = commandPrefix.first()
            val executableName = runCatching { Path.of(executable).fileName?.toString().orEmpty().lowercase() }
                .getOrDefault(executable.lowercase())
            return when {
                executableName.startsWith("poetry") -> listOf(executable, "run", "pip", "install")
                executableName.startsWith("pipenv") -> listOf(executable, "run", "pip", "install")
                executableName.startsWith("uv") -> listOf(executable, "pip", "install")
                else -> listOf("python", "-m", "pip", "install")
            }
        }

        val executable = commandPrefix.first()
        val executableName = runCatching { Path.of(executable).fileName?.toString().orEmpty().lowercase() }
            .getOrDefault(executable.lowercase())
        if (executableName == "mkdocs" || executableName == "mkdocs.exe") {
            resolveVenvPythonForMkdocsExecutable(executable)?.let { pythonExecutable ->
                return listOf(pythonExecutable, "-m", "pip", "install")
            }
        }

        return listOf("python", "-m", "pip", "install")
    }

    private fun resolveAuthordVenvPython(siteRoot: Path?): String? {
        val root = siteRoot ?: return null
        val runtimeRoot = root.resolve(authordRuntimeVenvDirectoryName)
        val scriptPython = runtimeRoot.resolve("Scripts").resolve("python.exe")
        if (Files.exists(scriptPython) && Files.isRegularFile(scriptPython)) {
            return scriptPython.toString()
        }
        val binPython = runtimeRoot.resolve("bin").resolve("python")
        if (Files.exists(binPython) && Files.isRegularFile(binPython)) {
            return binPython.toString()
        }
        return if (isWindows()) scriptPython.toString() else binPython.toString()
    }

    private fun resolveVenvPythonForMkdocsExecutable(mkdocsExecutable: String): String? {
        val executablePath = runCatching { Path.of(mkdocsExecutable).toAbsolutePath().normalize() }.getOrNull() ?: return null
        val parent = executablePath.parent ?: return null
        val folder = parent.fileName?.toString()?.lowercase().orEmpty()
        if (folder != "bin" && folder != "scripts") {
            return null
        }
        val runtimeRoot = parent.parent ?: return null
        val scriptPython = runtimeRoot.resolve("Scripts").resolve("python.exe")
        if (Files.exists(scriptPython) && Files.isRegularFile(scriptPython)) {
            return scriptPython.toString()
        }
        val binPython = runtimeRoot.resolve("bin").resolve("python")
        if (Files.exists(binPython) && Files.isRegularFile(binPython)) {
            return binPython.toString()
        }
        return null
    }

    private fun mergeStartupOutput(
        diagnostics: RuntimeProcessDiagnostics?,
        startupOutput: String,
    ): String {
        return listOfNotNull(
            diagnostics?.stdoutOutput,
            diagnostics?.stderrOutput,
            diagnostics?.startupOutput,
            startupOutput,
        )
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun resolveRuntimeCandidates(projectPath: String, siteContext: SiteContext?): List<RuntimeCommandCandidate> {
        val normalizedProjectRoot = runCatching { Path.of(projectPath).toAbsolutePath().normalize() }.getOrNull()
        val normalizedSiteRoot = siteContext?.configPath
            ?.parent
            ?.let { path -> runCatching { path.toAbsolutePath().normalize() }.getOrNull() }
        val candidates = mutableListOf<RuntimeCommandCandidate>()
        val seenCommandPrefixes = linkedSetOf<String>()

        fun addExecutableCandidate(
            displayName: String,
            executablePath: String?,
            kind: RuntimeCandidateKind,
            siteRoot: Path? = null,
        ) {
            val executable = executablePath ?: return
            val commandPrefix = listOf(executable)
            val key = commandPrefix.joinToString("\u0000")
            if (seenCommandPrefixes.add(key)) {
                candidates += RuntimeCommandCandidate(
                    displayName = displayName,
                    commandPrefix = commandPrefix,
                    kind = kind,
                    siteRoot = siteRoot,
                )
            }
        }

        // Candidate order:
        // 1) site-level .authord_venv
        // 2) local .venv candidates
        // 3) non-local runtimes
        addExecutableCandidate(
            displayName = "$authordRuntimeVenvDirectoryName (site)",
            executablePath = normalizedSiteRoot?.let { resolveVenvMkdocsExecutable(it.resolve(authordRuntimeVenvDirectoryName)) },
            kind = RuntimeCandidateKind.AUTHORD_VENV,
            siteRoot = normalizedSiteRoot,
        )
        addExecutableCandidate(
            displayName = "$projectVenvDirectoryName (site)",
            executablePath = normalizedSiteRoot?.let { resolveVenvMkdocsExecutable(it.resolve(projectVenvDirectoryName)) },
            kind = RuntimeCandidateKind.LOCAL_VENV,
            siteRoot = normalizedSiteRoot,
        )
        addExecutableCandidate(
            displayName = "$projectVenvDirectoryName (project)",
            executablePath = normalizedProjectRoot?.let { resolveVenvMkdocsExecutable(it.resolve(projectVenvDirectoryName)) },
            kind = RuntimeCandidateKind.LOCAL_VENV,
            siteRoot = normalizedProjectRoot,
        )

        resolveShellCommandPrefix("mkdocs")?.let { commandPrefix ->
            val key = commandPrefix.joinToString("\u0000")
            if (seenCommandPrefixes.add(key)) {
                candidates += RuntimeCommandCandidate(
                    displayName = "mkdocs on PATH",
                    commandPrefix = commandPrefix,
                    kind = RuntimeCandidateKind.NON_LOCAL,
                )
            }
        }

        resolveShellCommandPrefix("poetry")?.let { poetry ->
            val commandPrefix = poetry + listOf("run", "mkdocs")
            val key = commandPrefix.joinToString("\u0000")
            if (seenCommandPrefixes.add(key)) {
                candidates += RuntimeCommandCandidate(
                    displayName = "poetry",
                    commandPrefix = commandPrefix,
                    kind = RuntimeCandidateKind.NON_LOCAL,
                )
            }
        }
        resolveShellCommandPrefix("pipenv")?.let { pipenv ->
            val commandPrefix = pipenv + listOf("run", "mkdocs")
            val key = commandPrefix.joinToString("\u0000")
            if (seenCommandPrefixes.add(key)) {
                candidates += RuntimeCommandCandidate(
                    displayName = "pipenv",
                    commandPrefix = commandPrefix,
                    kind = RuntimeCandidateKind.NON_LOCAL,
                )
            }
        }
        resolveShellCommandPrefix("uv")?.let { uv ->
            val commandPrefix = uv + listOf("run", "mkdocs")
            val key = commandPrefix.joinToString("\u0000")
            if (seenCommandPrefixes.add(key)) {
                candidates += RuntimeCommandCandidate(
                    displayName = "uv run",
                    commandPrefix = commandPrefix,
                    kind = RuntimeCandidateKind.NON_LOCAL,
                )
            }
        }

        return candidates
    }

    private fun resolveVenvMkdocsExecutable(venvPath: Path): String? {
        if (!venvPath.exists() || !Files.isDirectory(venvPath)) {
            return null
        }
        val executableCandidates = listOf(
            venvPath.resolve("Scripts").resolve("mkdocs.exe"),
            venvPath.resolve("Scripts").resolve("mkdocs"),
            venvPath.resolve("bin").resolve("mkdocs"),
        )
        return executableCandidates
            .firstOrNull { Files.exists(it) && Files.isRegularFile(it) }
            ?.toString()
    }

    private fun looksLikeCommandUnavailable(raw: String): Boolean {
        if (raw.isBlank()) {
            return false
        }
        val normalized = raw.lowercase()
        return "command not found" in normalized ||
            "not recognized as an internal or external command" in normalized ||
            "createprocess error=2" in normalized ||
            "no such file or directory" in normalized ||
            "cannot find the file" in normalized ||
            "is not recognized" in normalized
    }

    private fun resolveShellCommandPrefix(command: String): List<String>? {
        val executable = resolveExistingCommandPath(command)
        if (executable != null) {
            return listOf(executable)
        }
        return if (isRunningUnderGradleTest()) listOf(command) else null
    }

    private fun resolveExistingCommandPath(command: String): String? {
        if (command.isBlank()) {
            return null
        }

        val pathCandidate = resolveCommandFromPath(command)
        if (pathCandidate != null) {
            return pathCandidate
        }
        val commonCandidate = resolveCommandFromKnownLocations(command)
        if (commonCandidate != null) {
            return commonCandidate
        }
        return null
    }

    private fun resolveCommandFromPath(command: String): String? {
        val pathValue = System.getenv("PATH").orEmpty().trim()
        if (pathValue.isEmpty()) {
            return null
        }
        return pathValue
            .split(File.pathSeparator)
            .asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .mapNotNull { rawDir -> runCatching { Path.of(rawDir) }.getOrNull() }
            .flatMap { directory -> resolveCommandCandidatesInDirectory(directory, command) }
            .firstOrNull { candidate -> isRunnableCommandFile(candidate) }
            ?.toString()
    }

    private fun resolveCommandFromKnownLocations(command: String): String? {
        return commonExecutableDirectories()
            .asSequence()
            .flatMap { directory -> resolveCommandCandidatesInDirectory(directory, command) }
            .firstOrNull { candidate -> isRunnableCommandFile(candidate) }
            ?.toString()
    }

    private fun resolveCommandCandidatesInDirectory(directory: Path, command: String): Sequence<Path> {
        if (isWindows() && !command.contains('.')) {
            return windowsExecutableExtensions()
                .asSequence()
                .map { extension -> directory.resolve(command + extension) }
            }
        return sequenceOf(directory.resolve(command))
    }

    private fun isRunnableCommandFile(path: Path): Boolean {
        return runCatching {
            Files.exists(path) &&
                Files.isRegularFile(path) &&
                (isWindows() || Files.isExecutable(path))
        }.getOrDefault(false)
    }

    private fun commonExecutableDirectories(): List<Path> {
        val candidates = mutableListOf<Path>()
        val home = System.getProperty("user.home").takeIf { !it.isNullOrBlank() }
            ?: System.getenv("HOME")?.takeIf { it.isNotBlank() }

        if (home != null) {
            candidates.add(Path.of(home, ".local", "bin"))
            candidates.add(Path.of(home, ".cargo", "bin"))
        }

        if (isWindows()) {
            val userProfile = System.getenv("USERPROFILE")?.takeIf { it.isNotBlank() }
            if (userProfile != null) {
                candidates.add(Path.of(userProfile, "AppData", "Local", "Microsoft", "WindowsApps"))
            }
        } else {
            candidates.add(Path.of("/opt", "homebrew", "bin"))
            candidates.add(Path.of("/usr", "local", "bin"))
            candidates.add(Path.of("/usr", "bin"))
            candidates.add(Path.of("/bin"))
        }

        return candidates
            .asSequence()
            .mapNotNull { candidate -> runCatching { candidate.toAbsolutePath().normalize() }.getOrNull() }
            .filter { directory -> runCatching { Files.exists(directory) && Files.isDirectory(directory) }.getOrDefault(false) }
            .distinct()
            .toList()
    }

    private fun isRunningUnderGradleTest(): Boolean = System.getProperty("org.gradle.test.worker") != null

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)

    private fun windowsExecutableExtensions(): List<String> {
        val configured = System.getenv("PATHEXT")
            ?.split(';')
            ?.map(String::trim)
            ?.filter { it.isNotBlank() }
            ?.map { if (it.startsWith(".")) it else ".$it" }
            .orEmpty()
        if (configured.isNotEmpty()) {
            return configured
        }
        return listOf(".exe", ".cmd", ".bat", ".com")
    }

    private fun noRunnableRuntimeMessage(unavailableCandidates: List<String>): String {
        val base = AuthordUiBundle.message("activation.error.noRunnableRuntime")
        if (unavailableCandidates.isEmpty()) {
            return base
        }
        return "$base Tried: ${unavailableCandidates.joinToString(", ")}."
    }

    private fun buildSetupGuidanceForMissingConfig(projectPath: String): String {
        val configError = AuthordUiBundle.message("activation.error.configNotFound", projectPath)
        return buildString {
            append(configError)
            append(" Open the Authord Tool Window and use Setup Mode to create the project configuration.")
        }
    }

    private fun waitUntilUp(projectId: String, baseUrl: String): StartupReadiness {
        val pollInterval = startupPollIntervalMillis.coerceIn(100L, 250L)
        val timeout = startupProbeTimeoutMillis.coerceAtLeast(1_000L)
        val deadline = nowMillisProvider() + timeout
        var latestDiagnostics = processManager.diagnostics(projectId)
        val normalizedExpectedBaseUrl = normalizeBaseUrl(baseUrl)
        var loggedBaseUrlMismatch = false

        while (nowMillisProvider() <= deadline) {
            latestDiagnostics = processManager.diagnostics(projectId) ?: latestDiagnostics
            if (latestDiagnostics != null && !latestDiagnostics.isAlive) {
                return StartupReadiness.ProcessExited(latestDiagnostics)
            }
            if (hasPortBindConflict(latestDiagnostics?.startupOutput.orEmpty())) {
                return StartupReadiness.ProcessExited(latestDiagnostics)
            }

            val advertisedBaseUrl = detectAdvertisedBaseUrl(latestDiagnostics)
            if (!loggedBaseUrlMismatch &&
                advertisedBaseUrl != null &&
                normalizeBaseUrl(advertisedBaseUrl) != normalizedExpectedBaseUrl
            ) {
                LOG.warn(
                    "Authord runtime advertised URL '$advertisedBaseUrl' while probing expected '$baseUrl' for projectId=$projectId",
                )
                loggedBaseUrlMismatch = true
            }

            if (readinessProbe.isReady(baseUrl)) {
                val postProbeDiagnostics = processManager.diagnostics(projectId) ?: latestDiagnostics
                if (postProbeDiagnostics != null && !postProbeDiagnostics.isAlive) {
                    return StartupReadiness.ProcessExited(postProbeDiagnostics)
                }
                if (hasPortBindConflict(postProbeDiagnostics?.startupOutput.orEmpty())) {
                    return StartupReadiness.ProcessExited(postProbeDiagnostics)
                }
                return StartupReadiness.Ready(postProbeDiagnostics)
            }

            if (!safeSleep(pollInterval)) {
                break
            }
        }

        return StartupReadiness.TimedOut(processManager.diagnostics(projectId) ?: latestDiagnostics)
    }

    private fun detectAdvertisedBaseUrl(diagnostics: RuntimeProcessDiagnostics?): String? {
        if (diagnostics == null) {
            return null
        }
        val mergedOutput = listOf(
            diagnostics.stdoutOutput,
            diagnostics.stderrOutput,
            diagnostics.startupOutput,
        ).joinToString("\n")
        return baseUrlDetector.detectBaseUrl(mergedOutput)
    }

    private fun resolveCanonicalPreviewBaseUrl(
        expectedBaseUrl: String,
        diagnostics: RuntimeProcessDiagnostics?,
    ): String {
        val normalizedExpectedBaseUrl = normalizeBaseUrl(expectedBaseUrl)
        val advertisedBaseUrl = detectAdvertisedBaseUrl(diagnostics) ?: return normalizedExpectedBaseUrl
        val normalizedAdvertisedBaseUrl = normalizeBaseUrl(advertisedBaseUrl)
        if (normalizedAdvertisedBaseUrl == normalizedExpectedBaseUrl) {
            return normalizedExpectedBaseUrl
        }
        if (!isTrustedAdvertisedBaseUrl(
                expectedBaseUrl = normalizedExpectedBaseUrl,
                advertisedBaseUrl = normalizedAdvertisedBaseUrl,
            )
        ) {
            return normalizedExpectedBaseUrl
        }
        val advertisedPath = runCatching { URI.create(normalizedAdvertisedBaseUrl).path.orEmpty() }
            .getOrElse { return normalizedExpectedBaseUrl }
        return if (advertisedPath.isBlank() || advertisedPath == "/") {
            normalizedExpectedBaseUrl
        } else {
            normalizedAdvertisedBaseUrl
        }
    }

    private fun isTrustedAdvertisedBaseUrl(
        expectedBaseUrl: String,
        advertisedBaseUrl: String,
    ): Boolean {
        val expectedUri = runCatching { URI.create(expectedBaseUrl) }.getOrNull() ?: return false
        val advertisedUri = runCatching { URI.create(advertisedBaseUrl) }.getOrNull() ?: return false
        if (!expectedUri.scheme.equals(advertisedUri.scheme, ignoreCase = true)) {
            return false
        }
        val expectedPort = effectivePort(expectedUri)
        val advertisedPort = effectivePort(advertisedUri)
        if (expectedPort <= 0 || expectedPort != advertisedPort) {
            return false
        }
        val expectedHost = expectedUri.host?.trim().orEmpty()
        val advertisedHost = advertisedUri.host?.trim().orEmpty()
        if (expectedHost.isBlank() || advertisedHost.isBlank()) {
            return false
        }
        return isLoopbackHost(expectedHost) && isLoopbackHost(advertisedHost)
    }

    private fun effectivePort(uri: URI): Int {
        val explicit = uri.port
        if (explicit > 0) {
            return explicit
        }
        return when (uri.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }

    private fun isLoopbackHost(host: String): Boolean {
        return when (host.lowercase()) {
            "localhost" -> true
            else -> runCatching {
                val resolved = InetAddress.getByName(host)
                resolved.isLoopbackAddress || resolved.isAnyLocalAddress
            }.getOrDefault(false)
        }
    }

    private fun hasPortBindConflict(output: String): Boolean {
        if (output.isBlank()) {
            return false
        }
        val normalized = output.lowercase()
        return "address already in use" in normalized || "errno 98" in normalized || "winerror 10048" in normalized
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun startupFailureDetails(projectPath: String, failure: StartupAttemptFailure?): String {
        if (failure == null) {
            return startFailureDetails(projectPath, "")
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
        val parsedError = startFailureDetails(projectPath, mergedOutput)
        val stdoutTail = tailText(diagnostics?.stdoutOutput.orEmpty())
        val stderrTail = tailText(diagnostics?.stderrOutput.orEmpty())

        return buildString {
            append(failure.failureSummary)
            append(" Last attempted URL: ")
            append(failure.baseUrl.ifBlank { "<unavailable>" })
            append(". Process exit code: ")
            append(diagnostics?.exitCode?.toString() ?: "unavailable")

            if (parsedError.isNotBlank()) {
                append(". ")
                append(parsedError)
            }

            if (stdoutTail.isNotBlank()) {
                append(". stdout tail:\n")
                append(stdoutTail)
            }

            if (stderrTail.isNotBlank()) {
                append("\nstderr tail:\n")
                append(stderrTail)
            }
        }
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
            themeNotInstalledRegex.containsMatchIn(normalizedOutput)
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

        val hasConfigFile = resolveConfigPath(projectPath) != null
        if (!hasConfigFile) {
            return AuthordUiBundle.message("activation.error.configNotFound", projectPath)
        }

        return ""
    }

    private fun isMaterializedProjectRoot(projectPath: String): Boolean {
        val rootPath = runCatching { Path.of(projectPath) }.getOrNull() ?: return false
        return rootPath.exists() && Files.isDirectory(rootPath)
    }

    private fun buildServeCommand(
        projectId: String,
        projectPath: String,
        siteContext: SiteContext?,
        commandPrefix: List<String>,
        host: String,
        port: Int,
    ): List<String> {
        val activeConfigPath = siteContext?.configPath
        val fallbackThemeConfigPath = ensureFallbackThemeConfig(
            projectId = projectId,
            projectPath = projectPath,
            siteContext = siteContext,
        )
        val servedConfigPath = fallbackThemeConfigPath ?: activeConfigPath
        val configArgs = if (servedConfigPath != null) {
            listOf("-f", servedConfigPath.toString())
        } else {
            emptyList()
        }
        val hostBindingArgs = listOf(mkdocsDevAddrFlag, "$host:$port")

        return commandPrefix + listOf("serve") + hostBindingArgs + configArgs + listOf(
            "--livereload",
            "--dirty",
        )
    }

    private fun ensureFallbackThemeConfig(
        projectId: String,
        projectPath: String,
        siteContext: SiteContext?,
    ): Path? {
        val baseConfigPath = siteContext?.configPath ?: return null
        val shouldApplyThemeOverrides = shouldUseDefaultThemeOverrides(siteContext)
        val fallbackSiteName = fallbackSiteName(projectPath, baseConfigPath)
        if (!shouldApplyThemeOverrides && fallbackSiteName == null) {
            return null
        }
        val resolvedBaseConfigPath = baseConfigPath.toAbsolutePath().normalize().toString()
        val resolvedDocsDirPath = siteContext.docsDirPath.toAbsolutePath().normalize().toString()
        val fallbackThemeConfigPath = pluginScopedThemeConfigPath(projectId, projectPath)
        val fallbackColorMode = if (runCatching { isDarkIdeTheme() }.getOrDefault(false)) "dark" else "light"
        val fallbackConfig = buildString {
            append("INHERIT: '")
            append(escapeSingleQuotedYaml(resolvedBaseConfigPath))
            append("'\n")
            append("docs_dir: '")
            append(escapeSingleQuotedYaml(resolvedDocsDirPath))
            append("'\n")
            if (fallbackSiteName != null) {
                append("site_name: '${escapeSingleQuotedYaml(fallbackSiteName)}'\n")
            }
            if (shouldApplyThemeOverrides) {
                append("theme:\n")
                append("  name: mkdocs\n")
                append("  color_mode: $fallbackColorMode\n")
                append("  user_color_mode_toggle: true\n")
            }
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

        if (wroteFallbackConfig && fallbackSiteName != null) {
            LOG.info("Authord preview applied plugin-scoped fallback site_name for config: ${baseConfigPath.toAbsolutePath().normalize()}")
        }
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

    private fun shouldUseDefaultThemeOverrides(siteContext: SiteContext?): Boolean {
        val configPath = siteContext?.configPath ?: return true
        val existing = runCatching { Files.readString(configPath) }.getOrNull() ?: return true
        return existing.lineSequence().none { line ->
            val trimmed = line.trimStart()
            trimmed.isNotEmpty() &&
                !trimmed.startsWith("#") &&
                themeKeyRegex.containsMatchIn(trimmed)
        }
    }

    private fun fallbackSiteName(projectPath: String, configPath: Path): String? {
        val existing = runCatching { Files.readString(configPath) }.getOrNull() ?: return null
        val hasSiteName = existing.lineSequence().any { line ->
            val trimmed = line.trimStart()
            trimmed.isNotEmpty() && !trimmed.startsWith("#") && siteNameKeyRegex.containsMatchIn(trimmed)
        }
        if (hasSiteName) {
            return null
        }
        return defaultSiteName(Path.of(projectPath))
    }

    private fun resolveConfigPath(projectPath: String): Path? {
        return resolveSiteContext(projectPath)?.configPath
    }

    private fun resolveSiteContext(projectPath: String): SiteContext? {
        val rootPath = runCatching { Path.of(projectPath).toAbsolutePath().normalize() }.getOrNull() ?: return null
        return siteContextResolver.resolveProjectDefault(rootPath)
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

    /**
     * Releases project-scoped bootstrap cache entries.
     */
    fun disposeProjectResources(projectPath: String) {
        bootstrapService.evict(projectPath)
    }

    private fun loopbackHostAddress(): String {
        val resolved = runCatching { InetAddress.getLoopbackAddress().hostAddress }.getOrDefault("127.0.0.1")
        if (resolved.isBlank() || resolved.contains(':')) {
            return "127.0.0.1"
        }
        return resolved
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(PluginActivationService::class.java)
    }
}
