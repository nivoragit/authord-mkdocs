package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownAutoOpenPreviewStartupActivityTest {
    @Test
    fun `markdown selection auto-starts preview and opens tool window by default`() {
        val baseDir = Files.createTempDirectory("authord-auto-open")
        Files.writeString(baseDir.resolve("mkdocs.yml"), "site_name: test")
        val project = IntellijTestFixtures.project(basePath = baseDir.toString())

        var started = 0
        val navigated = mutableListOf<String>()
        var toolWindowOpened = 0
        val activity = MarkdownAutoOpenPreviewStartupActivity(
            autoOpenEnabledProvider = { true },
            canStartPreviewProvider = { true },
            previewStarter = {
                started += 1
                true
            },
            previewNavigator = { _, path -> navigated += path },
            toolWindowOpener = { toolWindowOpened += 1 },
            delayedInvoker = { _, task -> task() },
        )

        activity.onMarkdownFileSelected(project, "${baseDir}/docs/index.md")

        assertEquals(1, started)
        assertEquals(listOf("${baseDir}/docs/index.md"), navigated)
        assertEquals(1, toolWindowOpened)
    }

    @Test
    fun `auto-open ignores non-markdown files`() {
        val baseDir = Files.createTempDirectory("authord-auto-open")
        Files.writeString(baseDir.resolve("mkdocs.yml"), "site_name: test")
        val project = IntellijTestFixtures.project(basePath = baseDir.toString())

        var started = 0
        val activity = MarkdownAutoOpenPreviewStartupActivity(
            autoOpenEnabledProvider = { true },
            canStartPreviewProvider = { true },
            previewStarter = {
                started += 1
                true
            },
            delayedInvoker = { _, task -> task() },
        )

        activity.onMarkdownFileSelected(project, "${baseDir}/README.txt")

        assertEquals(0, started)
    }

    @Test
    fun `auto-open respects disabled setting`() {
        val baseDir = Files.createTempDirectory("authord-auto-open")
        Files.writeString(baseDir.resolve("mkdocs.yml"), "site_name: test")
        val project = IntellijTestFixtures.project(basePath = baseDir.toString())

        var started = 0
        val activity = MarkdownAutoOpenPreviewStartupActivity(
            autoOpenEnabledProvider = { false },
            canStartPreviewProvider = { true },
            previewStarter = {
                started += 1
                true
            },
            delayedInvoker = { _, task -> task() },
        )

        activity.onMarkdownFileSelected(project, "${baseDir}/docs/index.md")

        assertEquals(0, started)
    }

    @Test
    fun `rapid markdown selections debounce to latest path and single startup`() {
        val baseDir = Files.createTempDirectory("authord-auto-open")
        Files.writeString(baseDir.resolve("mkdocs.yml"), "site_name: test")
        val project = IntellijTestFixtures.project(basePath = baseDir.toString())

        val pending = mutableListOf<() -> Unit>()
        var started = 0
        val navigated = mutableListOf<String>()
        val activity = MarkdownAutoOpenPreviewStartupActivity(
            autoOpenEnabledProvider = { true },
            canStartPreviewProvider = { true },
            previewStarter = {
                started += 1
                true
            },
            previewNavigator = { _, path -> navigated += path },
            delayedInvoker = { _, task -> pending += task },
            toolWindowOpener = { },
        )

        val firstPath = "${baseDir}/docs/first.md"
        val secondPath = "${baseDir}/docs/second.md"
        activity.onMarkdownFileSelected(project, firstPath)
        activity.onMarkdownFileSelected(project, secondPath)

        pending.forEach { it.invoke() }

        assertEquals(1, started)
        assertEquals(listOf(secondPath), navigated)
    }

    @Test
    fun `eligibility helpers require markdown under project with mkdocs config`() {
        val baseDir = Files.createTempDirectory("authord-auto-open")
        Files.writeString(baseDir.resolve("mkdocs.yml"), "site_name: test")

        assertTrue(MarkdownAutoOpenPreviewStartupActivity.isMarkdownPath("${baseDir}/docs/a.md"))
        assertTrue(MarkdownAutoOpenPreviewStartupActivity.hasMkdocsConfig(baseDir.toString()))
        assertTrue(
            MarkdownAutoOpenPreviewStartupActivity.isUnderProject(
                baseDir.toString(),
                "${baseDir}/docs/a.md",
            ),
        )
    }
}
