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
    fun `emits ordered flow states when route resolves successfully`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-flow-ready")
        val targetUrl = "http://127.0.0.1:8000/new/"
        val states = mutableListOf<PreviewRouteFlowState>()

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { _, _ -> Unit },
            routeReadyProbe = { true },
            maxAttempts = 1,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onStateChanged = { state, _ -> states += state },
            onRouteUnavailable = { _ -> },
        )

        assertEquals(
            listOf(
                PreviewRouteFlowState.INTENT_ACCEPTED,
                PreviewRouteFlowState.WAITING_FOR_ROUTE_READINESS,
                PreviewRouteFlowState.ROUTE_READY,
                PreviewRouteFlowState.LOADED,
            ),
            states,
        )
    }

    @Test
    fun `loads once after transient 404 window when route eventually becomes ready`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-transient")
        val targetUrl = "http://127.0.0.1:8000/renamed/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var targetRouteProbes = 0
        var warning: String? = null

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = { url ->
                if (url != targetUrl) {
                    false
                } else {
                    targetRouteProbes += 1
                    targetRouteProbes >= 3
                }
            },
            maxAttempts = 5,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false), loaded)
        assertEquals(3, targetRouteProbes)
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

        assertTrue(loaded.isEmpty())
        assertTrue(probes >= 4)
        val warningMessage = warning
        assertNotNull(warningMessage)
        assertTrue(warningMessage.contains("/renamed/"))
    }

    @Test
    fun `loads html fallback route when directory style route stays unavailable`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-html-fallback")
        val targetUrl = "http://127.0.0.1:8000/new/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var warning: String? = null
        val probes = mutableListOf<String>()

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = { url ->
                probes += url
                url == "http://127.0.0.1:8000/new.html"
            },
            maxAttempts = 2,
            retryDelayMillis = 0L,
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { true },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(
            listOf(
                "http://127.0.0.1:8000/new.html" to true,
            ),
            loaded,
        )
        assertTrue(probes.contains("http://127.0.0.1:8000/new.html"))
        assertNull(warning)
    }

    @Test
    fun `dirty livereload mode waits with extended window and resolves delayed route`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-dirty-livereload")
        val targetUrl = "http://127.0.0.1:8000/df/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var warning: String? = null
        var probes = 0
        var nowMillis = 0L
        var nudges = 0

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = {
                probes += 1
                probes >= 7
            },
            maxAttempts = 1,
            retryDelayMillis = 0L,
            dirtyLivereloadMode = true,
            dirtyLivereloadMaxWaitMillis = 10_000L,
            dirtyLivereloadInitialDelayMillis = 200L,
            dirtyLivereloadMaxDelayMillis = 600L,
            onDirtyLivereloadRouteMiss = { nudges += 1 },
            nowMillisProvider = { nowMillis },
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { delay ->
                nowMillis += delay
                true
            },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false), loaded)
        assertTrue(probes >= 7)
        assertTrue(nudges >= 1)
        assertNull(warning)
    }

    @Test
    fun `dirty livereload mode replays pending route after initial unavailability`() {
        val project = IntellijTestFixtures.project(locationHash = "preview-route-dirty-livereload-replay")
        val targetUrl = "http://127.0.0.1:8000/replayed/"
        val loaded = mutableListOf<Pair<String, Boolean>>()
        var warning: String? = null
        var probes = 0
        var nowMillis = 0L
        var nudges = 0

        loadPreviewRouteWithReadinessGuard(
            project = project,
            targetUrl = targetUrl,
            isRequestCurrent = { true },
            isRuntimeRunning = { true },
            loadUrl = { url, forceReload -> loaded += url to forceReload },
            routeReadyProbe = {
                probes += 1
                probes >= 7
            },
            maxAttempts = 1,
            retryDelayMillis = 0L,
            dirtyLivereloadMode = true,
            dirtyLivereloadMaxWaitMillis = 1_000L,
            dirtyLivereloadInitialDelayMillis = 200L,
            dirtyLivereloadMaxDelayMillis = 200L,
            dirtyLivereloadReplayAttempts = 1,
            dirtyLivereloadReplayDelayMillis = 0L,
            onDirtyLivereloadRouteMiss = { nudges += 1 },
            nowMillisProvider = { nowMillis },
            backgroundRunner = { task -> task() },
            uiRunner = { task -> task() },
            sleeper = { delay ->
                nowMillis += delay
                true
            },
            onRouteUnavailable = { message -> warning = message },
        )

        assertEquals(listOf(targetUrl to false), loaded)
        assertTrue(probes >= 7)
        assertTrue(nudges >= 2)
        assertNull(warning)
    }
}
