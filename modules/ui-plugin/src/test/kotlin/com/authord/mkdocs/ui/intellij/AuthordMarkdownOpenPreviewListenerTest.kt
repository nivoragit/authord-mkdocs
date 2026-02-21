package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.Project
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthordMarkdownOpenPreviewListenerTest {
    @Test
    fun `docs markdown open activates browser service and starts runtime`() {
        val projectRoot = Files.createTempDirectory("markdown-open-listener-runtime")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            Files.writeString(projectRoot.resolve("docs/index.md"), "# Home\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "markdown-open-runtime")
            val runtimeService = PluginRuntimeIntegrationService(project)
            runtimeService.setStartupOutputForNextRun("ready at https://preview.example/")
            val browserService = MkDocsPreviewBrowserService(project)
            val settingsService = AuthordPreviewSettingsService()

            val listener = AuthordMarkdownOpenPreviewListener(
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
                settingsServiceResolver = { settingsService },
            )

            invokeOnMarkdownOpened(
                listener = listener,
                project = project,
                selectedPath = projectRoot.resolve("docs/index.md").toString().replace('\\', '/'),
            )

            assertTrue(browserService.markdownPreviewActivated())
            assertTrue(runtimeService.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `non-docs markdown open is ignored`() {
        val projectRoot = Files.createTempDirectory("markdown-open-listener-ignore")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            Files.writeString(projectRoot.resolve("README.md"), "# Readme\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "markdown-open-ignore")
            val runtimeService = PluginRuntimeIntegrationService(project)
            runtimeService.setStartupOutputForNextRun("ready at https://preview.example/")
            val browserService = MkDocsPreviewBrowserService(project)
            val settingsService = AuthordPreviewSettingsService()

            val listener = AuthordMarkdownOpenPreviewListener(
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
                settingsServiceResolver = { settingsService },
            )

            invokeOnMarkdownOpened(
                listener = listener,
                project = project,
                selectedPath = projectRoot.resolve("README.md").toString().replace('\\', '/'),
            )

            assertFalse(browserService.markdownPreviewActivated())
            assertFalse(runtimeService.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `listener respects auto open preview setting`() {
        val projectRoot = Files.createTempDirectory("markdown-open-listener-setting")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            Files.writeString(projectRoot.resolve("docs/index.md"), "# Home\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "markdown-open-setting")
            val runtimeService = PluginRuntimeIntegrationService(project)
            runtimeService.setStartupOutputForNextRun("ready at https://preview.example/")
            val browserService = MkDocsPreviewBrowserService(project)
            val settingsService = AuthordPreviewSettingsService().apply {
                autoOpenPreviewOnMarkdownOpen = false
            }

            val listener = AuthordMarkdownOpenPreviewListener(
                runtimeServiceResolver = { runtimeService },
                browserServiceResolver = { browserService },
                settingsServiceResolver = { settingsService },
            )

            invokeOnMarkdownOpened(
                listener = listener,
                project = project,
                selectedPath = projectRoot.resolve("docs/index.md").toString().replace('\\', '/'),
            )

            assertFalse(browserService.markdownPreviewActivated())
            assertFalse(runtimeService.isRuntimeRunning())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    private fun invokeOnMarkdownOpened(
        listener: AuthordMarkdownOpenPreviewListener,
        project: Project,
        selectedPath: String,
    ) {
        val method = listener::class.java.getDeclaredMethod(
            "onMarkdownOpened",
            Project::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(listener, project, selectedPath)
    }
}
