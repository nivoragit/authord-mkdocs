package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.testFramework.LightVirtualFile
import java.nio.file.Files
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AuthordMarkdownSplitEditorProviderTest {
    private class RecordingPreviewContent : PreviewContent {
        override val component = JPanel()
        val loadedUrls = mutableListOf<String>()

        override fun loadUrl(url: String) {
            loadedUrls += url
        }
    }

    private fun injectPreviewContent(service: MkDocsPreviewBrowserService, preview: PreviewContent) {
        val field = MkDocsPreviewBrowserService::class.java.getDeclaredField("previewContent")
        field.isAccessible = true
        field.set(service, preview)
    }

    private fun invokeLoadUrlIfChanged(editor: AuthordMarkdownPreviewFileEditor, url: String, forceReload: Boolean = false) {
        val method = AuthordMarkdownPreviewFileEditor::class.java.getDeclaredMethod(
            "loadUrlIfChanged",
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(editor, url, forceReload)
    }

    @Test
    fun `split preview eligibility requires mkdocs config`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider")
        try {
            val filePath = projectRoot.resolve("docs").resolve("index.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-eligibility-config")

            assertFalse(isAuthordPreviewEligible(project, filePath))

            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\n")
            Files.createDirectories(projectRoot.resolve("docs"))
            invalidateMkdocsConfigCache(projectRoot)
            assertTrue(isAuthordPreviewEligible(project, filePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `split preview eligibility rejects nested config that does not scope selected markdown`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider-nested-config")
        try {
            val filePath = projectRoot.resolve("docs").resolve("index.md").toString()
            val nestedConfigDir = projectRoot.resolve("site")
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-eligibility-nested")
            Files.createDirectories(nestedConfigDir)
            Files.writeString(nestedConfigDir.resolve("_mkdocs.yml"), "site_name: docs\n")

            invalidateMkdocsConfigCache(projectRoot)
            assertFalse(isAuthordPreviewEligible(project, filePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `split preview eligibility rejects markdown outside the project root`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider-in")
        val outsideRoot = Files.createTempDirectory("authord-split-provider-out")
        try {
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-eligibility-outside")
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\n")
            Files.createDirectories(projectRoot.resolve("docs"))
            val outsideFilePath = outsideRoot.resolve("docs").resolve("index.md").toString()

            assertFalse(isAuthordPreviewEligible(project, outsideFilePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
            outsideRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `split preview eligibility accepts docs scoped markdown only`() {
        val projectRoot = Files.createTempDirectory("authord-split-provider-docs-scope")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val docsFile = projectRoot.resolve("docs/guide.md").toString()
            val nonDocsFile = projectRoot.resolve("README.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-eligibility-docs-only")

            assertTrue(isAuthordPreviewEligible(project, docsFile))
            assertFalse(isAuthordPreviewEligible(project, nonDocsFile))
        } finally {
            projectRoot.toFile().deleteRecursively()
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
    fun `preferred layout defaults to editor and preview when project setting was never stored`() {
        val project = IntellijTestFixtures.project(
            locationHash = "split-layout-empty",
            services = mapOf(AuthordSplitEditorLayoutStateService::class.java to AuthordSplitEditorLayoutStateService()),
        )

        val preferred = preferredAuthordSplitLayout(project)

        assertEquals(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW, preferred)
    }

    @Test
    fun `store layout persists shared preference for project`() {
        val service = AuthordSplitEditorLayoutStateService()
        val project = IntellijTestFixtures.project(
            locationHash = "split-layout-store",
            services = mapOf(AuthordSplitEditorLayoutStateService::class.java to service),
        )
        val candidate = TextEditorWithPreview.Layout.entries.firstOrNull {
            it != TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW
        } ?: TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW

        storeAuthordSplitLayout(project, candidate)

        assertEquals(candidate, preferredAuthordSplitLayout(project))
        assertEquals(candidate.name, service.getState().preferredLayoutName)
    }

    @Test
    fun `invalid stored layout value is ignored`() {
        val preferred = parseAuthordSplitLayoutName("INVALID_LAYOUT")

        assertEquals(null, preferred)
    }

    @Test
    fun `split editors reuse one shared browser service preview surface`() {
        val projectRoot = Files.createTempDirectory("authord-split-editor-shared-browser")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\ndocs_dir: docs\n")
            val fileOnePath = projectRoot.resolve("docs").resolve("one.md").toString().replace('\\', '/')
            val fileTwoPath = projectRoot.resolve("docs").resolve("two.md").toString().replace('\\', '/')

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-shared-browser")
            val runtimeService = PluginRuntimeIntegrationService(project)
            val browserService = MkDocsPreviewBrowserService(project)
            var fallbackFactoryInvocations = 0
            val editorOne = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("one.md") {
                    override fun getPath(): String = fileOnePath
                },
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
                previewContentFactory = {
                    fallbackFactoryInvocations += 1
                    object : PreviewContent {
                        override val component = JPanel()
                        override fun loadUrl(url: String) = Unit
                    }
                },
            )
            val editorTwo = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("two.md") {
                    override fun getPath(): String = fileTwoPath
                },
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
                previewContentFactory = {
                    fallbackFactoryInvocations += 1
                    object : PreviewContent {
                        override val component = JPanel()
                        override fun loadUrl(url: String) = Unit
                    }
                },
            )

            val shared = browserService.ensurePreviewContent()
            assertEquals(0, fallbackFactoryInvocations)

            editorOne.selectNotify()
            assertSame(editorOne.component, shared.component.parent)

            editorOne.deselectNotify()
            editorTwo.selectNotify()
            assertSame(editorTwo.component, shared.component.parent)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `switching editors reloads shared browser route when returning to previous file`() {
        val projectRoot = Files.createTempDirectory("authord-split-editor-switch-routes")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\ndocs_dir: docs\n")
            val fileOnePath = projectRoot.resolve("docs").resolve("one.md").toString().replace('\\', '/')
            val fileTwoPath = projectRoot.resolve("docs").resolve("two.md").toString().replace('\\', '/')

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "split-shared-browser-switch")
            val runtimeService = PluginRuntimeIntegrationService(project)
            val browserService = MkDocsPreviewBrowserService(project)
            val preview = RecordingPreviewContent()
            injectPreviewContent(browserService, preview)

            val editorOne = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("one.md") {
                    override fun getPath(): String = fileOnePath
                },
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
            )
            val editorTwo = AuthordMarkdownPreviewFileEditor(
                project = project,
                file = object : LightVirtualFile("two.md") {
                    override fun getPath(): String = fileTwoPath
                },
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
            )

            invokeLoadUrlIfChanged(editorOne, "http://127.0.0.1:8000/one/")
            invokeLoadUrlIfChanged(editorTwo, "http://127.0.0.1:8000/two/")
            invokeLoadUrlIfChanged(editorOne, "http://127.0.0.1:8000/one/")

            assertEquals(
                listOf(
                    "http://127.0.0.1:8000/one/",
                    "http://127.0.0.1:8000/two/",
                    "http://127.0.0.1:8000/one/",
                ),
                preview.loadedUrls,
            )
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
