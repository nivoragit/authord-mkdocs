package com.authord.mkdocs.ui.intellij

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI

private const val DEFAULT_ROUTE_READY_ATTEMPTS: Int = 24
private const val DEFAULT_ROUTE_READY_DELAY_MS: Long = 150L
private const val DEFAULT_DIRTY_LIVERELOAD_MAX_WAIT_MS: Long = 30_000L
private const val DEFAULT_DIRTY_LIVERELOAD_INITIAL_DELAY_MS: Long = 150L
private const val DEFAULT_DIRTY_LIVERELOAD_MAX_DELAY_MS: Long = 1_500L
private const val DEFAULT_DIRTY_LIVERELOAD_REPLAY_ATTEMPTS: Int = 2
private const val DEFAULT_DIRTY_LIVERELOAD_REPLAY_DELAY_MS: Long = 1_000L

enum class PreviewRouteFlowState {
    INTENT_ACCEPTED,
    WAITING_FOR_ROUTE_READINESS,
    ROUTE_READY,
    LOADED,
    UNAVAILABLE,
    CANCELLED,
}

private sealed interface RouteReadinessOutcome {
    object Cancelled : RouteReadinessOutcome
    object Unavailable : RouteReadinessOutcome
    data class Ready(val url: String, val forceReload: Boolean) : RouteReadinessOutcome
}

/**
 * Waits until a local preview route is reachable before loading it in the browser.
 *
 * In MkDocs `--livereload --dirty` mode, route publication can lag behind file creation and editor
 * selection events; this coordinator uses an extended deadline-based probe mode to absorb that lag.
 */
internal fun loadPreviewRouteWithReadinessGuard(
    project: Project,
    targetUrl: String,
    isRequestCurrent: () -> Boolean,
    isRuntimeRunning: () -> Boolean,
    loadUrl: (url: String, forceReload: Boolean) -> Unit,
    routeReadyProbe: (String) -> Boolean = { url -> isPreviewRouteReady(url) },
    maxAttempts: Int = DEFAULT_ROUTE_READY_ATTEMPTS,
    retryDelayMillis: Long = DEFAULT_ROUTE_READY_DELAY_MS,
    dirtyLivereloadMode: Boolean = false,
    dirtyLivereloadMaxWaitMillis: Long = DEFAULT_DIRTY_LIVERELOAD_MAX_WAIT_MS,
    dirtyLivereloadInitialDelayMillis: Long = DEFAULT_DIRTY_LIVERELOAD_INITIAL_DELAY_MS,
    dirtyLivereloadMaxDelayMillis: Long = DEFAULT_DIRTY_LIVERELOAD_MAX_DELAY_MS,
    dirtyLivereloadReplayAttempts: Int = DEFAULT_DIRTY_LIVERELOAD_REPLAY_ATTEMPTS,
    dirtyLivereloadReplayDelayMillis: Long = DEFAULT_DIRTY_LIVERELOAD_REPLAY_DELAY_MS,
    onDirtyLivereloadRouteMiss: (() -> Unit)? = null,
    nowMillisProvider: () -> Long = System::currentTimeMillis,
    backgroundRunner: ((() -> Unit) -> Unit) = ::runPreviewRouteTaskInBackground,
    uiRunner: ((() -> Unit) -> Unit) = ::runPreviewRouteTaskOnUi,
    sleeper: (Long) -> Boolean = ::sleepPreviewRouteRetry,
    onStateChanged: ((PreviewRouteFlowState, String) -> Unit)? = null,
    onRouteUnavailable: ((String) -> Unit)? = { message ->
        presentAuthordNotification(project, message, NotificationType.WARNING)
    },
) {
    val normalizedUrl = normalizeTargetUrl(targetUrl)
    if (normalizedUrl.isEmpty()) {
        return
    }

    if (project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()) {
        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.CANCELLED, normalizedUrl)
        return
    }

    emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.INTENT_ACCEPTED, normalizedUrl)

    if (!PreviewNavigationPolicy.isLocalUrl(normalizedUrl)) {
        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.ROUTE_READY, normalizedUrl)
        loadUrl(normalizedUrl, false)
        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.LOADED, normalizedUrl)
        return
    }

    val candidateUrls = candidateRouteUrls(normalizedUrl)
    backgroundRunner {
        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.WAITING_FOR_ROUTE_READINESS, normalizedUrl)
        val replayAttempts = if (dirtyLivereloadMode) {
            dirtyLivereloadReplayAttempts.coerceAtLeast(0)
        } else {
            0
        }
        var remainingReplays = replayAttempts
        var probeOutcome: RouteReadinessOutcome

        while (true) {
            probeOutcome = if (dirtyLivereloadMode) {
                awaitDirtyLivereloadRoute(
                    candidateUrls = candidateUrls,
                    primaryUrl = normalizedUrl,
                    routeReadyProbe = routeReadyProbe,
                    maxWaitMillis = dirtyLivereloadMaxWaitMillis,
                    initialDelayMillis = dirtyLivereloadInitialDelayMillis,
                    maxDelayMillis = dirtyLivereloadMaxDelayMillis,
                    onRouteMiss = onDirtyLivereloadRouteMiss,
                    nowMillisProvider = nowMillisProvider,
                    sleeper = sleeper,
                    isCancelled = {
                        project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()
                    },
                )
            } else {
                awaitStandardRoute(
                    candidateUrls = candidateUrls,
                    primaryUrl = normalizedUrl,
                    routeReadyProbe = routeReadyProbe,
                    maxAttempts = maxAttempts,
                    retryDelayMillis = retryDelayMillis,
                    sleeper = sleeper,
                    isCancelled = {
                        project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()
                    },
                )
            }

            if (probeOutcome !is RouteReadinessOutcome.Unavailable || remainingReplays <= 0) {
                break
            }
            if (project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()) {
                probeOutcome = RouteReadinessOutcome.Cancelled
                break
            }
            remainingReplays -= 1
            val replayDelay = dirtyLivereloadReplayDelayMillis.coerceAtLeast(0L)
            if (replayDelay > 0L && !sleeper(replayDelay)) {
                probeOutcome = RouteReadinessOutcome.Cancelled
                break
            }
            emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.WAITING_FOR_ROUTE_READINESS, normalizedUrl)
        }

        when (probeOutcome) {
            is RouteReadinessOutcome.Cancelled -> {
                emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.CANCELLED, normalizedUrl)
                return@backgroundRunner
            }
            is RouteReadinessOutcome.Ready -> {
                emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.ROUTE_READY, probeOutcome.url)
                uiRunner {
                    if (!project.isDisposed && isRequestCurrent() && isRuntimeRunning()) {
                        loadUrl(probeOutcome.url, probeOutcome.forceReload)
                        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.LOADED, probeOutcome.url)
                    } else {
                        emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.CANCELLED, probeOutcome.url)
                    }
                }
                return@backgroundRunner
            }
            is RouteReadinessOutcome.Unavailable -> {
                emitPreviewRouteState(onStateChanged, PreviewRouteFlowState.UNAVAILABLE, normalizedUrl)
            }
        }

        val unavailableCallback = onRouteUnavailable ?: return@backgroundRunner
        val routeLabel = routePathLabel(normalizedUrl)
        uiRunner {
            if (!project.isDisposed && isRequestCurrent() && isRuntimeRunning()) {
                unavailableCallback("Preview route '$routeLabel' is not ready yet.")
            }
        }
    }
}

private fun emitPreviewRouteState(
    listener: ((PreviewRouteFlowState, String) -> Unit)?,
    state: PreviewRouteFlowState,
    url: String,
) {
    runCatching { listener?.invoke(state, url) }
}

/**
 * Returns true only when an HTTP route responds with non-404 success/redirect status.
 */
internal fun isPreviewRouteReady(
    routeUrl: String,
    connectTimeoutMillis: Int = 300,
    readTimeoutMillis: Int = 300,
): Boolean {
    if (!PreviewNavigationPolicy.isLocalUrl(routeUrl)) {
        return true
    }

    val connection = runCatching {
        URI.create(routeUrl).toURL().openConnection() as HttpURLConnection
    }.getOrNull() ?: return false

    return try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.useCaches = false
        connection.connect()
        val status = connection.responseCode
        status in 200..399
    } catch (_: Exception) {
        false
    } finally {
        connection.disconnect()
    }
}

private fun routePathLabel(url: String): String {
    return runCatching {
        val uri = URI.create(url)
        val path = uri.path?.trim().orEmpty().ifBlank { "/" }
        if (path.isEmpty()) "/" else path
    }.getOrDefault(url)
}

private fun candidateRouteUrls(url: String): List<String> {
    val uri = runCatching { URI.create(url) }.getOrNull() ?: return emptyList()
    val path = normalizeLocalRoutePath(uri.path)
    val candidatePaths = linkedSetOf<String>()
    candidatePaths += path

    if (path == "/") {
        return listOf(url)
    }

    val withoutTrailingSlash = path.trimEnd('/')
    if (withoutTrailingSlash.isNotEmpty()) {
        candidatePaths += "$withoutTrailingSlash/"
        candidatePaths += "$withoutTrailingSlash/index.html"
        candidatePaths += "$withoutTrailingSlash.html"
    }

    if (path.endsWith(".html", ignoreCase = true)) {
        val withoutHtml = path.dropLast(5).trimEnd('/')
        if (withoutHtml.isNotEmpty()) {
            candidatePaths += "$withoutHtml/"
            candidatePaths += "$withoutHtml/index.html"
        }
    }

    return candidatePaths.mapNotNull { candidatePath ->
        runCatching {
            URI(
                uri.scheme,
                uri.authority,
                normalizeCandidatePath(candidatePath),
                uri.query,
                uri.fragment,
            ).toString()
        }.getOrNull()
    }
}

private fun awaitStandardRoute(
    candidateUrls: List<String>,
    primaryUrl: String,
    routeReadyProbe: (String) -> Boolean,
    maxAttempts: Int,
    retryDelayMillis: Long,
    sleeper: (Long) -> Boolean,
    isCancelled: () -> Boolean,
): RouteReadinessOutcome {
    val attempts = maxAttempts.coerceAtLeast(1)
    for (attempt in 1..attempts) {
        if (isCancelled()) {
            return RouteReadinessOutcome.Cancelled
        }

        for (candidate in candidateUrls) {
            if (isCancelled()) {
                return RouteReadinessOutcome.Cancelled
            }
            val ready = runCatching { routeReadyProbe(candidate) }.getOrDefault(false)
            if (ready) {
                return RouteReadinessOutcome.Ready(
                    url = candidate,
                    forceReload = candidate != primaryUrl,
                )
            }
        }

        if (attempt < attempts && retryDelayMillis > 0L) {
            if (!sleeper(retryDelayMillis)) {
                return RouteReadinessOutcome.Cancelled
            }
        }
    }

    return RouteReadinessOutcome.Unavailable
}

private fun awaitDirtyLivereloadRoute(
    candidateUrls: List<String>,
    primaryUrl: String,
    routeReadyProbe: (String) -> Boolean,
    maxWaitMillis: Long,
    initialDelayMillis: Long,
    maxDelayMillis: Long,
    onRouteMiss: (() -> Unit)?,
    nowMillisProvider: () -> Long,
    sleeper: (Long) -> Boolean,
    isCancelled: () -> Boolean,
): RouteReadinessOutcome {
    val deadline = nowMillisProvider() + maxWaitMillis.coerceAtLeast(1_000L)
    var delay = initialDelayMillis.coerceIn(100L, maxDelayMillis.coerceAtLeast(100L))
    val maxDelay = maxDelayMillis.coerceAtLeast(delay)
    var nextNudgeAt = nowMillisProvider()

    while (true) {
        if (isCancelled()) {
            return RouteReadinessOutcome.Cancelled
        }

        for (candidate in candidateUrls) {
            if (isCancelled()) {
                return RouteReadinessOutcome.Cancelled
            }
            val ready = runCatching { routeReadyProbe(candidate) }.getOrDefault(false)
            if (ready) {
                return RouteReadinessOutcome.Ready(
                    url = candidate,
                    forceReload = candidate != primaryUrl,
                )
            }
        }

        val nowForNudge = nowMillisProvider()
        if (nowForNudge >= nextNudgeAt) {
            runCatching { onRouteMiss?.invoke() }
            nextNudgeAt = nowForNudge + delay.coerceAtLeast(100L)
        }

        val now = nowMillisProvider()
        if (now >= deadline) {
            return RouteReadinessOutcome.Unavailable
        }

        val sleepMillis = minOf(delay, deadline - now)
        if (sleepMillis > 0L && !sleeper(sleepMillis)) {
            return RouteReadinessOutcome.Cancelled
        }
        delay = minOf(maxDelay, (delay * 3L) / 2L)
    }
}

private fun normalizeTargetUrl(targetUrl: String): String {
    val trimmed = targetUrl.trim()
    if (trimmed.isEmpty()) {
        return ""
    }
    if (!PreviewNavigationPolicy.isLocalUrl(trimmed)) {
        return trimmed
    }

    val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return trimmed
    val normalizedPath = normalizeLocalRoutePath(uri.path)
    return runCatching {
        URI(
            uri.scheme,
            uri.authority,
            normalizedPath,
            uri.query,
            uri.fragment,
        ).toString()
    }.getOrDefault(trimmed)
}

private fun normalizeLocalRoutePath(rawPath: String?): String {
    val path = rawPath?.trim().orEmpty()
    if (path.isEmpty()) {
        return "/"
    }

    var normalized = path
        .replace('\\', '/')
        .replace(Regex("/{2,}"), "/")
    if (!normalized.startsWith("/")) {
        normalized = "/$normalized"
    }
    if (normalized != "/" &&
        !normalized.endsWith("/") &&
        !normalized.endsWith(".html", ignoreCase = true)
    ) {
        normalized += "/"
    }
    return normalized
}

private fun normalizeCandidatePath(rawPath: String): String {
    val cleaned = rawPath.trim().replace('\\', '/')
    if (cleaned.isEmpty()) {
        return "/"
    }
    val collapsed = cleaned.replace(Regex("/{2,}"), "/")
    return if (collapsed.startsWith("/")) collapsed else "/$collapsed"
}

private fun runPreviewRouteTaskInBackground(task: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app != null) {
        app.executeOnPooledThread(task)
        return
    }

    Thread(task, "authord-preview-route-load").apply {
        isDaemon = true
        start()
    }
}

private fun runPreviewRouteTaskOnUi(task: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app != null) {
        app.invokeLater(task, ModalityState.any())
        return
    }
    task()
}

private fun sleepPreviewRouteRetry(delayMillis: Long): Boolean {
    return try {
        Thread.sleep(delayMillis)
        true
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }
}
