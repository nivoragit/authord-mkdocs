package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

enum class PreviewOwnerKind {
    TOOL_WINDOW,
    SPLIT_EDITOR,
}

/**
 * Project-scoped owner for the tool-window preview surface.
 *
 * Keeps a single [PreviewContent] instance for the project lifecycle so
 * hide/show or tool-window mode changes do not recreate the embedded browser.
 */
@Service(Service.Level.PROJECT)
class MkDocsPreviewBrowserService(
    @Suppress("unused")
    private val project: Project,
) : Disposable {
    private data class OwnerRegistryEntry(
        val ownerKey: String,
        val ownerKind: PreviewOwnerKind,
        val content: OwnedPreviewContent,
        var requestedActive: Boolean = false,
        var activationOrder: Long = 0L,
    )

    private var previewContent: PreviewContent? = null
    private var markdownActivated: Boolean = false
    private var sharedLastLoadedUrl: String? = null
    private var coalescingLastLoadedUrl: String? = null
    private val reloadCoalescingGate = ReloadCoalescingGate()
    private val ownersByKey = linkedMapOf<String, OwnerRegistryEntry>()
    private var activeOwnerKey: String? = null
    private var activationCounter: Long = 0L

    /**
     * Returns existing preview content or creates it once for this project.
     */
    fun ensurePreviewContent(): PreviewContent {
        previewContent?.let { return it }
        synchronized(this) {
            previewContent?.let { return it }
            val created = runCatching {
                createDefaultPreviewContent(onMainFrameLoadEnd = reloadCoalescingGate::recordReload)
            }.getOrElse {
                // Tests may run without full IntelliJ/JCEF runtime.
                LOG.warn("Authord preview browser initialization failed; falling back to NoOpPreviewContent.", it)
                NoOpPreviewContent()
            }
            previewContent = created
            return created
        }
    }

    /**
     * Returns preview content only when already initialized.
     */
    fun previewContentOrNull(): PreviewContent? = synchronized(this) { previewContent }

    /**
     * Returns whether preview content has been initialized at least once.
     */
    fun hasInitializedPreviewContent(): Boolean = synchronized(this) { previewContent != null }

    /**
     * Marks preview as markdown-activated and ensures preview surface exists.
     */
    fun markMarkdownActivated(): PreviewContent {
        synchronized(this) {
            markdownActivated = true
            return ensurePreviewContent()
        }
    }

    /**
     * Returns whether markdown open has activated preview usage for this project.
     */
    fun markdownPreviewActivated(): Boolean = synchronized(this) { markdownActivated }

    /**
     * Loads URL in the persistent preview surface.
     */
    fun loadUrl(url: String, forceReload: Boolean = false) {
        val previewToLoad = synchronized(this) {
            if (!forceReload && sharedLastLoadedUrl == url) {
                return
            }
            if (!forceReload &&
                coalescingLastLoadedUrl == url &&
                reloadCoalescingGate.shouldSuppressReload()
            ) {
                return
            }
            sharedLastLoadedUrl = url
            coalescingLastLoadedUrl = url
            markdownActivated = true
            ensurePreviewContent()
        }
        previewToLoad.loadUrl(url)
    }

    /**
     * Clears shared URL dedupe cache so the next load request is always applied.
     */
    fun resetLastLoadedUrl() {
        synchronized(this) {
            sharedLastLoadedUrl = null
        }
    }

    /**
     * Returns owner-scoped preview content host that reuses one shared browser instance.
     */
    fun previewContentForOwner(ownerKind: PreviewOwnerKind, ownerId: String): PreviewContent {
        val key = ownerKey(ownerKind, ownerId)
        synchronized(this) {
            val existing = ownersByKey[key]
            if (existing != null) {
                return existing.content
            }
            val created = OwnedPreviewContent(ownerKey = key, ownerKind = ownerKind)
            ownersByKey[key] = OwnerRegistryEntry(
                ownerKey = key,
                ownerKind = ownerKind,
                content = created,
            )
            return created
        }
    }

    /**
     * Marks an owner as active and reparents the shared preview browser to its host panel.
     */
    fun activatePreviewOwner(ownerKind: PreviewOwnerKind, ownerId: String): Boolean {
        val key = ownerKey(ownerKind, ownerId)
        synchronized(this) {
            val entry = ownersByKey[key] ?: return false
            entry.requestedActive = true
            entry.activationOrder = ++activationCounter
            recomputeActiveOwnerLocked()
            return activeOwnerKey == key
        }
    }

    /**
     * Marks an owner as inactive; ownership may fall back to another active owner.
     */
    fun deactivatePreviewOwner(ownerKind: PreviewOwnerKind, ownerId: String) {
        val key = ownerKey(ownerKind, ownerId)
        synchronized(this) {
            val entry = ownersByKey[key] ?: return
            entry.requestedActive = false
            recomputeActiveOwnerLocked()
        }
    }

    /**
     * Releases owner registration and detaches host bindings.
     */
    fun releasePreviewOwner(ownerKind: PreviewOwnerKind, ownerId: String) {
        val key = ownerKey(ownerKind, ownerId)
        synchronized(this) {
            val removed = ownersByKey.remove(key) ?: return
            removed.content.disposeHostOnly()
            if (activeOwnerKey == key) {
                activeOwnerKey = null
            }
            recomputeActiveOwnerLocked()
        }
    }

    override fun dispose() {
        synchronized(this) {
            ownersByKey.values.forEach { entry ->
                entry.content.disposeHostOnly()
            }
            ownersByKey.clear()
            activeOwnerKey = null
            activationCounter = 0L
            previewContent?.dispose()
            previewContent = null
            sharedLastLoadedUrl = null
            coalescingLastLoadedUrl = null
            markdownActivated = false
        }
    }

    private fun ownerKey(ownerKind: PreviewOwnerKind, ownerId: String): String {
        return "${ownerKind.name}:$ownerId"
    }

    private fun ownerPriority(ownerKind: PreviewOwnerKind): Int {
        return when (ownerKind) {
            PreviewOwnerKind.SPLIT_EDITOR -> 2
            PreviewOwnerKind.TOOL_WINDOW -> 1
        }
    }

    private fun recomputeActiveOwnerLocked() {
        val next = ownersByKey.values
            .asSequence()
            .filter { it.requestedActive }
            .maxWithOrNull(
                compareBy<OwnerRegistryEntry>(
                    { ownerPriority(it.ownerKind) },
                    { it.activationOrder },
                ),
            )
        val nextKey = next?.ownerKey
        val currentKey = activeOwnerKey
        if (currentKey == nextKey && next != null) {
            bindListenersToOwnerLocked(next)
            attachSharedComponentToOwnerLocked(next)
            return
        }

        activeOwnerKey = nextKey
        if (next == null) {
            previewContent?.setContentReloadListener(null)
            previewContent?.setManualScrollListener(null)
            return
        }

        attachSharedComponentToOwnerLocked(next)
        bindListenersToOwnerLocked(next)
    }

    private fun attachSharedComponentToOwnerLocked(owner: OwnerRegistryEntry) {
        val sharedPreview = ensurePreviewContent()
        val ownerHost = owner.content.hostPanel
        val sharedComponent = sharedPreview.component
        val previousParent = sharedComponent.parent as? JComponent
        if (sharedComponent.parent !== ownerHost) {
            ownerHost.removeAll()
            ownerHost.add(sharedComponent, BorderLayout.CENTER)
            ownerHost.revalidate()
            ownerHost.repaint()
            if (previousParent != null && previousParent !== ownerHost) {
                previousParent.revalidate()
                previousParent.repaint()
            }
        }
    }

    private fun bindListenersToOwnerLocked(owner: OwnerRegistryEntry) {
        val sharedPreview = ensurePreviewContent()
        sharedPreview.setContentReloadListener(owner.content.contentReloadCallback)
        sharedPreview.setManualScrollListener(owner.content.manualScrollCallback)
    }

    private fun updateActiveOwnerListenersLocked(ownerKey: String) {
        if (activeOwnerKey != ownerKey) {
            return
        }
        val active = ownersByKey[ownerKey] ?: return
        bindListenersToOwnerLocked(active)
    }

    private inner class OwnedPreviewContent(
        private val ownerKey: String,
        private val ownerKind: PreviewOwnerKind,
    ) : PreviewContent {
        val hostPanel: JPanel = JPanel(BorderLayout())
        @Volatile var contentReloadCallback: (() -> Unit)? = null
        @Volatile var manualScrollCallback: ((Double) -> Unit)? = null

        override val component: JComponent = hostPanel

        override fun loadUrl(url: String) {
            this@MkDocsPreviewBrowserService.loadUrl(url)
        }

        override fun loadSetupPage(onProjectCreate: (String) -> Unit) {
            ensurePreviewContent().loadSetupPage(onProjectCreate)
        }

        override fun scrollToProgress(progress: Double) {
            ensurePreviewContent().scrollToProgress(progress)
        }

        override fun scrollToY(y: Double, syncToken: Long?) {
            ensurePreviewContent().scrollToY(y, syncToken)
        }

        override fun requestScrollMetrics(callback: (PreviewScrollMetrics?) -> Unit) {
            ensurePreviewContent().requestScrollMetrics(callback)
        }

        override fun requestDomSnapshot(callback: (PreviewDomSnapshot?) -> Unit) {
            ensurePreviewContent().requestDomSnapshot(callback)
        }

        override fun setContentReloadListener(listener: (() -> Unit)?) {
            contentReloadCallback = listener
            synchronized(this@MkDocsPreviewBrowserService) {
                updateActiveOwnerListenersLocked(ownerKey)
            }
        }

        override fun setManualScrollListener(listener: ((Double) -> Unit)?) {
            manualScrollCallback = listener
            synchronized(this@MkDocsPreviewBrowserService) {
                updateActiveOwnerListenersLocked(ownerKey)
            }
        }

        override fun supportsPreviewEditorSync(): Boolean = ensurePreviewContent().supportsPreviewEditorSync()

        override fun prefersSetupPanel(): Boolean = ensurePreviewContent().prefersSetupPanel()

        override fun dispose() {
            val ownerId = ownerKey.substringAfter(':', "")
            this@MkDocsPreviewBrowserService.releasePreviewOwner(ownerKind, ownerId)
        }

        fun disposeHostOnly() {
            hostPanel.removeAll()
            hostPanel.revalidate()
            hostPanel.repaint()
            contentReloadCallback = null
            manualScrollCallback = null
        }
    }

    private class NoOpPreviewContent : PreviewContent {
        override val component: JComponent = JPanel(BorderLayout())

        override fun loadUrl(url: String) = Unit
    }

    private companion object {
        private val LOG: Logger = Logger.getInstance(MkDocsPreviewBrowserService::class.java)
    }
}
