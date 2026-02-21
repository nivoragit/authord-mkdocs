package com.authord.mkdocs.ui.intellij

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import java.net.HttpURLConnection
import java.net.URI

private const val DEFAULT_ROUTE_READY_ATTEMPTS: Int = 24
private const val DEFAULT_ROUTE_READY_DELAY_MS: Long = 150L

/**
 * Loads [targetUrl] immediately, then retries readiness and forces one reload after route becomes available.
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
    backgroundRunner: ((() -> Unit) -> Unit) = ::runPreviewRouteTaskInBackground,
    uiRunner: ((() -> Unit) -> Unit) = ::runPreviewRouteTaskOnUi,
    sleeper: (Long) -> Boolean = ::sleepPreviewRouteRetry,
    onRouteUnavailable: ((String) -> Unit)? = { message ->
        presentAuthordNotification(project, message, NotificationType.WARNING)
    },
) {
    val normalizedUrl = targetUrl.trim()
    if (normalizedUrl.isEmpty()) {
        return
    }

    if (project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()) {
        return
    }

    loadUrl(normalizedUrl, false)

    if (!PreviewNavigationPolicy.isLocalUrl(normalizedUrl)) {
        return
    }

    val attempts = maxAttempts.coerceAtLeast(1)
    backgroundRunner {
        var sawTransientUnavailable = false

        for (attempt in 1..attempts) {
            if (project.isDisposed || !isRequestCurrent() || !isRuntimeRunning()) {
                return@backgroundRunner
            }

            val ready = runCatching { routeReadyProbe(normalizedUrl) }.getOrDefault(false)
            if (ready) {
                if (sawTransientUnavailable) {
                    uiRunner {
                        if (!project.isDisposed && isRequestCurrent() && isRuntimeRunning()) {
                            loadUrl(normalizedUrl, true)
                        }
                    }
                }
                return@backgroundRunner
            }

            sawTransientUnavailable = true
            if (attempt < attempts && retryDelayMillis > 0L) {
                if (!sleeper(retryDelayMillis)) {
                    return@backgroundRunner
                }
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
        val path = uri.path?.trim().orEmpty()
        if (path.isEmpty()) "/" else path
    }.getOrDefault(url)
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
