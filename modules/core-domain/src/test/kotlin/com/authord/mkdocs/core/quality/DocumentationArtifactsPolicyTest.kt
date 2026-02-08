package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class DocumentationArtifactsPolicyTest {
    @Test
    fun `required SDD artifacts exist for this feature`() {
        val root = findRepoRoot()

        val required = listOf(
            "CHANGELOG.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-001-topic-tree-port.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-002-plugin-command-bus.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-003-preview-sync-port.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-004-vector-store-port.md",
            "specs/002-intellij-mkdocs-mvp/runtime-diagnostics-runbook.md",
            "specs/002-intellij-mkdocs-mvp/traceability-matrix.md",
            "docs/implementation/feature-spec.md",
            "docs/implementation/technical-design-notes.md",
            "docs/implementation/operational-runbook.md",
            "docs/implementation/test-plan-traceability.md",
            "docs/implementation/migration-notes.md",
        )

        required.forEach { relativePath ->
            assertTrue(Files.exists(root.resolve(relativePath)), "Missing artifact: $relativePath")
        }
    }

    @Test
    fun `documentation includes function usage guidance and traceability matrix`() {
        val root = findRepoRoot()

        val technicalNotes = Files.readString(root.resolve("docs/implementation/technical-design-notes.md"))
        val testPlan = Files.readString(root.resolve("docs/implementation/test-plan-traceability.md"))

        assertTrue(
            technicalNotes.contains("Function-Level Usage Guidance"),
            "Technical design notes must include function-level usage guidance",
        )
        assertTrue(
            testPlan.contains("Requirement-to-Test Traceability Matrix"),
            "Test plan must include requirement-to-test traceability matrix section",
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
