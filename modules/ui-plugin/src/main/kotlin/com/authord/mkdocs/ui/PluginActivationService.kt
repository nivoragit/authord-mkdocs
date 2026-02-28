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
 * Orchestrates first activation bootstrap, runtime startup, and preview opening.
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
    private data class StartupAttemptFailure(
        val baseUrl: String,
        val failureSummary: String,
        val diagnostics: RuntimeProcessDiagnostics?,
        val startupOutput: String,
    )

    private sealed interface StartupReadiness {
        data class Ready(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class ProcessExited(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness

        data class TimedOut(val diagnostics: RuntimeProcessDiagnostics?) : StartupReadiness
    }

    private val siteNameKeyRegex = Regex("""^\s*site_name\s*:""")
    private val themeKeyRegex = Regex("""^(?:theme|["']theme["'])\s*:""")
    private val themeNotInstalledRegex = Regex("""Theme '.*' is not installed""")
    private val fallbackThemeConfigFileName = ".authord.theme.yml"
    private val mkdocsDevAddrFlag = "--" + "dev-addr"
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
            val details = AuthordUiBundle.message("activation.error.configNotFound", projectPath)
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, details),
            )
        }

        val bootstrapResult = bootstrapService.bootstrap(projectPath)
        if (!bootstrapResult.success) {
            val reason = ActivationFailureReason.BOOTSTRAP_FAILED
            return ActivationResult(
                success = false,
                reason = reason,
                message = errorPresenter.present(reason, bootstrapResult.errorMessage),
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
            var shouldDelayBeforeRetry = true

            val startResult = processManager.start(
                projectId = projectId,
                workingDir = projectPath,
                config = RuntimeServerConfig(
                    command = parentBoundServeCommand(
                        projectId = projectId,
                        projectPath = projectPath,
                        siteContext = siteContext,
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
                    )
                }

                is StartupReadiness.ProcessExited -> {
                    logWarnings(readiness.diagnostics?.startupOutput.orEmpty())
                    shouldDelayBeforeRetry = !hasPortBindConflict(readiness.diagnostics?.startupOutput.orEmpty())
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

        val reason = ActivationFailureReason.START_FAILED
        val failureDetails = startupFailureDetails(projectPath, lastFailure)
        return ActivationResult(
            success = false,
            reason = reason,
            message = errorPresenter.present(reason, failureDetails),
        )
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

    private fun parentBoundServeCommand(
        projectId: String,
        projectPath: String,
        siteContext: SiteContext?,
        runtimePath: String,
        uvExecutablePath: String,
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

        return listOf(
            uvExecutablePath,
            "run",
            "--python",
            runtimePath,
            "mkdocs",
            "serve",
        ) + hostBindingArgs + configArgs + listOf(
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
