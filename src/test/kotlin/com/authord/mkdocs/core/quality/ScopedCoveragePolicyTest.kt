package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class ScopedCoveragePolicyTest {
    @Test
    fun `root build defines single-module coverage gate`() {
        val root = findRepoRoot()
        val buildContent = Files.readString(root.resolve("build.gradle.kts"))

        assertFalse(buildContent.contains(":modules:"), "Root build must not reference legacy module paths")
        assertTrue(buildContent.contains("jacocoTestCoverageVerification"))
        assertTrue(buildContent.contains("minimum = BigDecimal(\"0.9\")"))
    }

    @Test
    fun `legacy module build files are removed after demodularization`() {
        val root = findRepoRoot()
        val legacyModuleBuildFiles = listOf(
            "modules/core-domain/build.gradle.kts",
            "modules/extension-ports/build.gradle.kts",
            "modules/mkdocs-runtime-adapter/build.gradle.kts",
            "modules/ui-plugin/build.gradle.kts",
        )

        legacyModuleBuildFiles.forEach { relativePath ->
            assertFalse(
                Files.exists(root.resolve(relativePath)),
                "Legacy module build file should be removed: $relativePath",
            )
        }
    }

    @Test
    fun `ci workflows run fail on threshold coverage gates`() {
        val root = findRepoRoot()
        val qualityWorkflow = Files.readString(root.resolve(".github/workflows/quality.yml"))
        val ciWorkflow = Files.readString(root.resolve(".github/workflows/ci.yml"))

        assertFalse(qualityWorkflow.contains("scopedCoverageGate"))
        assertTrue(qualityWorkflow.contains("jacocoTestCoverageVerification"))
        assertTrue(ciWorkflow.contains("jacocoTestCoverageVerification"))
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
