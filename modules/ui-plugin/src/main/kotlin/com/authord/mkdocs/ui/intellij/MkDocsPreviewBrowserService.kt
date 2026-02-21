package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

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
    @Volatile
    private var previewContent: PreviewContent? = null
    @Volatile
    private var markdownActivated: Boolean = false

    /**
     * Returns existing preview content or creates it once for this project.
     */
    fun ensurePreviewContent(): PreviewContent {
        previewContent?.let { return it }
        synchronized(this) {
            previewContent?.let { return it }
            val created = runCatching { createDefaultPreviewContent() }.getOrElse {
                // Tests may run without full IntelliJ/JCEF runtime.
                NoOpPreviewContent()
            }
            previewContent = created
            return created
        }
    }

    /**
     * Returns preview content only when already initialized.
     */
    fun previewContentOrNull(): PreviewContent? = previewContent

    /**
     * Returns whether preview content has been initialized at least once.
     */
    fun hasInitializedPreviewContent(): Boolean = previewContent != null

    /**
     * Marks preview as markdown-activated and ensures preview surface exists.
     */
    fun markMarkdownActivated(): PreviewContent {
        markdownActivated = true
        return ensurePreviewContent()
    }

    /**
     * Returns whether markdown open has activated preview usage for this project.
     */
    fun markdownPreviewActivated(): Boolean = markdownActivated

    /**
     * Loads URL in the persistent preview surface.
     */
    fun loadUrl(url: String) {
        markMarkdownActivated().loadUrl(url)
    }

    override fun dispose() {
        synchronized(this) {
            previewContent?.dispose()
            previewContent = null
            markdownActivated = false
        }
    }

    private class NoOpPreviewContent : PreviewContent {
        override val component: JComponent = JPanel(BorderLayout())

        override fun loadUrl(url: String) = Unit
    }
}
