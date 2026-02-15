package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.core.topic.TopicTreeValidationIssueType
import com.authord.mkdocs.core.topic.TopicTreeValidationService
import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathCaseMatrixIntegrationTest {
    @Test
    fun `normalization uses canonical separators and resolves dot segments`() {
        val windows = PathPolicy.forOsName("Windows 11")
        val mac = PathPolicy.forOsName("macOS")
        val linux = PathPolicy.forOsName("Linux")

        assertEquals("docs/guide/index.md", windows.normalize("docs\\guide\\..\\guide/./index.md"))
        assertEquals("docs/guide/index.md", mac.normalize("docs\\guide\\..\\guide/./index.md"))
        assertEquals("docs/guide/index.md", linux.normalize("docs\\guide\\..\\guide/./index.md"))
    }

    @Test
    fun `comparison follows OS matrix case rules`() {
        val windows = PathPolicy.forOsName("Windows 11")
        val mac = PathPolicy.forOsName("macOS Sonoma")
        val linux = PathPolicy.forOsName("Linux")

        assertTrue(windows.equivalent("docs/Guide.md", "docs/guide.md"))
        assertTrue(mac.equivalent("docs/Guide.md", "docs/guide.md"))
        assertTrue(!linux.equivalent("docs/Guide.md", "docs/guide.md"))
    }

    @Test
    fun `case-only path differences follow validation behavior per OS`() {
        val nav = listOf(
            TopicNavNode(nodeId = "n1", title = "Guide", path = "Guide.md"),
        )

        val windowsValidation = TopicTreeValidationService(PathPolicy.forOsName("Windows"))
            .validate(navNodes = nav, docsRelativePaths = listOf("guide.md"))
        val linuxValidation = TopicTreeValidationService(PathPolicy.forOsName("Linux"))
            .validate(navNodes = nav, docsRelativePaths = listOf("guide.md"))

        assertTrue(windowsValidation.none { it.type == TopicTreeValidationIssueType.BROKEN_PATH })
        assertTrue(linuxValidation.any { it.type == TopicTreeValidationIssueType.BROKEN_PATH })
    }
}
