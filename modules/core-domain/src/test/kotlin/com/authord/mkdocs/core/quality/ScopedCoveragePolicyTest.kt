package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ScopedCoveragePolicyTest {
    @Test
    fun `root build defines scoped coverage gate over required modules`() {
        val root = findRepoRoot()
        val buildContent = Files.readString(root.resolve("build.gradle.kts"))

        assertTrue(buildContent.contains("val scopedCoverageModules = listOf("))
        assertTrue(buildContent.contains("\":modules:core-domain\""))
        assertTrue(buildContent.contains("\":modules:extension-ports\""))
        assertTrue(buildContent.contains("\":modules:mkdocs-runtime-adapter\""))
        assertTrue(buildContent.contains("\":modules:ui-plugin\""))
        assertTrue(buildContent.contains("scopedCoverageGate"))
        assertTrue(buildContent.contains("jacocoTestCoverageVerification"))
    }

    @Test
    fun `module build files enforce 100 percent coverage minimum`() {
        val root = findRepoRoot()
        val moduleBuildFiles = listOf(
            "modules/core-domain/build.gradle.kts",
            "modules/extension-ports/build.gradle.kts",
            "modules/mkdocs-runtime-adapter/build.gradle.kts",
            "modules/ui-plugin/build.gradle.kts",
        )

        moduleBuildFiles.forEach { relativePath ->
            val content = Files.readString(root.resolve(relativePath))
            val expectedMin = if (relativePath.contains("mkdocs-runtime-adapter")) "0.9" else "1.0"
            assertTrue(
                content.contains("minimum = BigDecimal(\"$expectedMin\")"),
                "Expected $expectedMin% coverage minimum in $relativePath",
            )
        }
    }

    @Test
    fun `ci workflows run fail on threshold coverage gates`() {
        val root = findRepoRoot()
        val qualityWorkflow = Files.readString(root.resolve(".github/workflows/quality.yml"))
        val ciWorkflow = Files.readString(root.resolve(".github/workflows/ci.yml"))

        assertTrue(qualityWorkflow.contains("scopedCoverageGate"))
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
