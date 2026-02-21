package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewRouteLoadCoordinatorTest {
    @Test
    fun `loads once when route is immediately ready`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-ready")
        val targetUrl = "http://127.0.0.1:8000/new/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var probes = 0
        var warning: String? = null

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = {
                probes += 1
                true
            },
            maxAttempts = 3,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false), loaded)
        assertEquals(1, probes)
        assertNull(warning)
    }

    @Test
    fun `forces one reload when route becomes ready after transient 404 window`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-transient")
        val targetUrl = "http://127.0.0.1:8000/renamed/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var probes = 0
        var warning: String? = null

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = {
                probes += 1
                probes >= 3
            },
            maxAttempts = 5,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false, targetUrl to true), loaded)
        assertEquals(3, probes)
        assertNull(warning)
    }

    @Test
    fun `warns once when route never becomes ready`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-unavailable")
        val targetUrl = "http://127.0.0.1:8000/renamed/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var probes = 0
        var warning: String? = null

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = {
                probes += 1
                false
            },
            maxAttempts = 4,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false), loaded)
        assertEquals(4, probes)
        val warningMessage = warning
        assertNotNull(warningMessage)
        assertTrue(warningMessage.contains("/renamed/"))
    }
}
