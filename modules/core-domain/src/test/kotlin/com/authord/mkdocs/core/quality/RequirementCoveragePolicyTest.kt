package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class RequirementCoveragePolicyTest {
    @Test
    fun `phase2 traceability matrix lists all required requirement families`() {
        val root = findRepoRoot()
        val traceability = Files.readString(root.resolve("specs/001-mkdocs-topic-tree/tasks.md"))

        val requiredIds = listOf(
            "R-01", "R-02", "R-03", "R-04", "R-04a", "R-05", "R-06", "R-07", "R-07a", "R-07b", "R-07c",
            "R-08", "R-09", "R-09a", "R-09b", "R-09c", "R-10", "R-11", "R-11a", "R-12", "R-12a", "R-12b",
            "R-12c", "R-12d", "R-12e", "R-13", "R-14", "R-15", "R-15a", "R-16", "R-17", "R-17a", "R-18",
            "R-19", "R-20", "R-21", "R-22",
            "NAV-001", "NAV-002", "NAV-003", "NAV-004",
            "PI-001", "PI-002", "PI-003",
            "DOC-001", "DOC-002", "DOC-003", "DOC-004", "DOC-005", "DOC-006",
            "SC-001", "SC-002", "SC-003", "SC-004", "SC-005", "SC-006", "SC-007",
        )

        requiredIds.forEach { id ->
            assertTrue(
                traceability.contains("| $id |"),
                "Missing traceability row for requirement ID: $id",
            )
        }
    }

    @Test
    fun `quality gate references jacoco coverage verification`() {
        val root = findRepoRoot()
        val buildFile = Files.readString(root.resolve("build.gradle.kts"))
        assertTrue(
            buildFile.contains("jacocoTestCoverageVerification"),
            "Root build must retain jacoco coverage verification gate",
        )
    }

    private fun findRepoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Repository root not found")
    }
}
