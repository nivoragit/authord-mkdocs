package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.testFramework.LightVirtualFile
import java.nio.file.Files
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthordMarkdownSplitEditorProviderTest {
    @Test
    fun `split preview eligibility requires mkdocs config`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider")
        try {
            val filePath = projectRoot.resolve("docs").resolve("index.md").toString()

            assertFalse(isAuthordMkdocsPreviewEligible(projectRoot.toString(), filePath))

            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\n")
            invalidateMkdocsConfigCache(projectRoot)
            assertTrue(isAuthordMkdocsPreviewEligible(projectRoot.toString(), filePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `split preview eligibility rejects markdown outside the project root`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider-in")
        val outsideRoot = Files.createTempDirectory("authord-split-provider-out")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\n")
            val outsideFilePath = outsideRoot.resolve("docs").resolve("index.md").toString()

            assertFalse(isAuthordMkdocsPreviewEligible(projectRoot.toString(), outsideFilePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
            outsideRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `refresh suppresses missing config notification when auto start is disabled`() {
        val projectRoot = Files.createTempDirectory("authord-split-editor-missing-config-silent")
        try {
            val docsPath = projectRoot.resolve("docs")
            Files.createDirectories(docsPath)
            val filePath = docsPath.resolve("index.md").toString().replace('\\', '/')
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val runtimeService = PluginRuntimeIntegrationService(project)
            val reported = mutableListOf<Pair<String, Boolean>>()
            val editor = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("index.md") {
                    override fun getPath(): String = filePath
                },
                runtimeServiceResolver = { runtimeService },
                resultPresenter = { _, message, success -> reported += message to success },
                previewContentFactory = {
                    object : PreviewContent {
                        override val component = JPanel()
                        override fun loadUrl(url: String) = Unit
                    }
                },
            )

            val refreshed = editor.refreshForSelectedFile(
                autoStart = false,
                trigger = PreviewStartTrigger.ACTION,
            )

            assertFalse(refreshed)
            assertTrue(reported.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `refresh reports missing config when auto start is enabled`() {
        val projectRoot = Files.createTempDirectory("authord-split-editor-missing-config-report")
        try {
            val docsPath = projectRoot.resolve("docs")
            Files.createDirectories(docsPath)
            val filePath = docsPath.resolve("index.md").toString().replace('\\', '/')
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString())
            val runtimeService = PluginRuntimeIntegrationService(project)
            val reported = mutableListOf<Pair<String, Boolean>>()
            val editor = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("index.md") {
                    override fun getPath(): String = filePath
                },
                runtimeServiceResolver = { runtimeService },
                resultPresenter = { _, message, success -> reported += message to success },
                previewContentFactory = {
                    object : PreviewContent {
                        override val component = JPanel()
                        override fun loadUrl(url: String) = Unit
                    }
                },
            )

            val refreshed = editor.refreshForSelectedFile(
                autoStart = true,
                trigger = PreviewStartTrigger.ACTION,
            )

            assertFalse(refreshed)
            assertEquals(1, reported.size)
            assertFalse(reported.single().second)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `preferred layout is absent when project setting was never stored`() {
        val project = IntellijTestFixtures.project(locationHash = "split-layout-empty")

        val preferred = preferredAuthordSplitLayout(project)

        assertEquals(null, preferred)
    }

    @Test
    fun `store layout persists shared preference for project`() {
        val project = IntellijTestFixtures.project(locationHash = "split-layout-store")
        val candidate = TextEditorWithPreview.Layout.entries.firstOrNull {
            it != TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
        } ?: TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW

        storeAuthordSplitLayout(project, candidate)

        assertEquals(candidate, preferredAuthordSplitLayout(project))
    }

    @Test
    fun `invalid stored layout value is ignored`() {
        val preferred = parseAuthordSplitLayoutName("INVALID_LAYOUT")

        assertEquals(null, preferred)
    }
}
