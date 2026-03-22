package com.authord.mkdocs.core.topic

import com.authord.mkdocs.ports.topic.TopicNavNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreeValidationServiceTest {
    private val service = TopicTreeValidationService(
        pathPolicy = PathPolicy(caseSensitiveComparison = true),
    )

    @Test
    fun `detects broken path references in nav`() {
        val issues = service.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "Missing", path = "missing.md"),
                TopicNavNode(nodeId = "n2", title = "Exists", path = "index.md"),
            ),
            docsRelativePaths = listOf("index.md"),
        )

        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.BROKEN_PATH && it.reference == "missing.md" })
    }

    @Test
    fun `detects duplicate nav references`() {
        val issues = service.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "Intro", path = "guide/intro.md"),
                TopicNavNode(nodeId = "n2", title = "Intro Again", path = "guide/intro.md"),
            ),
            docsRelativePaths = listOf("guide/intro.md"),
        )

        val duplicate = issues.first { it.type == TopicTreeValidationIssueType.DUPLICATE_REFERENCE }
        assertEquals("guide/intro.md", duplicate.reference)
    }

    @Test
    fun `detects malformed external links`() {
        val issues = service.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "Bad", externalUrl = "ht!tp://broken"),
                TopicNavNode(nodeId = "n2", title = "Good", externalUrl = "https://example.com/docs"),
            ),
            docsRelativePaths = emptyList(),
        )

        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.MALFORMED_LINK && it.reference == "ht!tp://broken" })
    }

    @Test
    fun `validate traverses nested children and reports missing child paths`() {
        val issues = service.validate(
            navNodes = listOf(
                TopicNavNode(
                    nodeId = "root",
                    title = "Root",
                    children = listOf(
                        TopicNavNode(nodeId = "child", title = "Child", path = "nested/missing.md"),
                    ),
                ),
            ),
            docsRelativePaths = listOf("nested/exists.md"),
        )

        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.BROKEN_PATH && it.reference == "nested/missing.md" })
    }

    @Test
    fun `validate deduplicates duplicates under case-insensitive policy`() {
        val insensitiveService = TopicTreeValidationService(
            pathPolicy = PathPolicy(caseSensitiveComparison = false),
        )

        val issues = insensitiveService.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "Intro A", path = "Guide/Intro.md"),
                TopicNavNode(nodeId = "n2", title = "Intro B", path = "guide/intro.md"),
            ),
            docsRelativePaths = listOf("guide/intro.md"),
        )

        val duplicateIssues = issues.filter { it.type == TopicTreeValidationIssueType.DUPLICATE_REFERENCE }
        assertEquals(1, duplicateIssues.size)
        assertEquals("Guide/Intro.md", duplicateIssues.single().reference)
    }

    @Test
    fun `default policy constructor and malformed uri branches are covered`() {
        val defaultService = TopicTreeValidationService()
        val issues = defaultService.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "No Scheme", externalUrl = "example.com/docs"),
                TopicNavNode(nodeId = "n2", title = "URI Exception", externalUrl = "https://exa mple.com/docs"),
            ),
            docsRelativePaths = emptyList(),
        )

        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.MALFORMED_LINK && it.reference == "example.com/docs" })
        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.MALFORMED_LINK && it.reference == "https://exa mple.com/docs" })
    }
}
