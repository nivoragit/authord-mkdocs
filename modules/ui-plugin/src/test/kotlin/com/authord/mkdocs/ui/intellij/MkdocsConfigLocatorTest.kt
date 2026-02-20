package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MkdocsConfigLocatorTest {
    @BeforeTest
    fun clearCacheBeforeEach() {
        clearMkdocsConfigCacheForTests()
    }

    @AfterTest
    fun clearCacheAfterEach() {
        clearMkdocsConfigCacheForTests()
    }

    @Test
    fun `findMkdocsConfig resolves project root config and prefers mkdocs yml over yaml`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-config-locator-priority-")
        try {
            val yamlPath = projectRoot.resolve("mkdocs.yaml")
            Files.writeString(yamlPath, "site_name: yaml\n")

            val firstResolved = findMkdocsConfig(projectRoot)
            assertNotNull(firstResolved)
            assertEquals(yamlPath.toAbsolutePath().normalize(), firstResolved.toAbsolutePath().normalize())

            val ymlPath = projectRoot.resolve("mkdocs.yml")
            Files.writeString(ymlPath, "site_name: yml\n")
            invalidateMkdocsConfigCache(projectRoot)

            val secondResolved = findMkdocsConfig(projectRoot)
            assertNotNull(secondResolved)
            assertEquals(ymlPath.toAbsolutePath().normalize(), secondResolved.toAbsolutePath().normalize())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `findMkdocsConfig ignores nested configs outside project root`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-config-locator-root-only-")
        try {
            val nested = projectRoot.resolve("nested")
            Files.createDirectories(nested)
            Files.writeString(nested.resolve("mkdocs.yml"), "site_name: nested\n")

            val resolved = findMkdocsConfig(projectRoot)
            assertNull(resolved)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `findMkdocsConfig uses cache and requires invalidation to observe config creation`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-config-locator-cache-")
        try {
            val initial = findMkdocsConfig(projectRoot)
            assertNull(initial)

            val createdConfig = projectRoot.resolve("mkdocs.yml")
            Files.writeString(createdConfig, "site_name: demo\n")

            val stale = findMkdocsConfig(projectRoot)
            assertNull(stale)

            invalidateMkdocsConfigCache(projectRoot)
            val refreshed = findMkdocsConfig(projectRoot)
            assertNotNull(refreshed)
            assertEquals(createdConfig.toAbsolutePath().normalize(), refreshed.toAbsolutePath().normalize())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `eligibility helpers validate markdown extension config presence and project boundary`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-config-locator-helpers-")
        val outsideRoot = createTempDirectory(prefix = "mkdocs-config-locator-helpers-outside-")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: docs\n")

            val markdownPath = projectRoot.resolve("docs").resolve("index.md").toString()
            val markdownLongPath = projectRoot.resolve("docs").resolve("guide.markdown").toString()
            val nonMarkdownPath = projectRoot.resolve("docs").resolve("notes.txt").toString()
            val outsidePath = outsideRoot.resolve("docs").resolve("index.md").toString()

            assertTrue(isMarkdownPath(markdownPath))
            assertTrue(isMarkdownPath(markdownLongPath))
            assertFalse(isMarkdownPath(nonMarkdownPath))
            assertTrue(hasMkdocsConfig(projectRoot.toString()))
            assertTrue(isUnderProject(projectRoot.toString(), markdownPath))
            assertFalse(isUnderProject(projectRoot.toString(), outsidePath))
        } finally {
            projectRoot.toFile().deleteRecursively()
            outsideRoot.toFile().deleteRecursively()
        }
    }
}
