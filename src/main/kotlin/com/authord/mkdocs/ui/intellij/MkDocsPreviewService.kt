package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.MkdocsProcessManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger

/**
 * Disposable runtime owner that guarantees Authord runtime shutdown on disposal.
 */
class MkDocsPreviewService(
    private val processManagerProvider: () -> MkdocsProcessManager,
    private val projectIdProvider: () -> String,
) : Disposable {
    /**
     * Stops the currently tracked server process for this project.
     */
    fun stopServer(): Boolean = processManagerProvider().stop(projectIdProvider())

    /**
     * Returns whether the project runtime process is currently alive.
     */
    fun isServerRunning(): Boolean = processManagerProvider().isRunning(projectIdProvider())

    override fun dispose() {
        val projectId = projectIdProvider()
        val stopped = stopServer()
        LOG.info("Disposed Authord preview service for $projectId (stopped=$stopped)")
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(MkDocsPreviewService::class.java)
    }
}
