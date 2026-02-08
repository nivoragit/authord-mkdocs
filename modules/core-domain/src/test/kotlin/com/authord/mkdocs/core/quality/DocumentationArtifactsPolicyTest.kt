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
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-001-topic-tree-port.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-002-plugin-command-bus.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-003-preview-sync-port.md",
            "specs/002-intellij-mkdocs-mvp/adrs/ADR-004-vector-store-port.md",
            "specs/002-intellij-mkdocs-mvp/runtime-diagnostics-runbook.md",
            "specs/002-intellij-mkdocs-mvp/traceability-matrix.md",
        )

        required.forEach { relativePath ->
            assertTrue(Files.exists(root.resolve(relativePath)), "Missing artifact: $relativePath")
        }
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
