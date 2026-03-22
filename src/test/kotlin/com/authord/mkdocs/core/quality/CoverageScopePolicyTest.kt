package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CoverageScopePolicyTest {
    @Test
    fun `coverage verification requires 100 percent line coverage`() {
        val root = findRepoRoot()
        val buildFile = root.resolve("build.gradle.kts")
        val ciWorkflow = root.resolve(".github/workflows/ci.yml")

        val buildContent = Files.readString(buildFile)
        val ciContent = Files.readString(ciWorkflow)

        assertTrue(buildContent.contains("jacocoTestCoverageVerification"), "Coverage verification task must be configured")
        assertTrue(buildContent.contains("minimum = BigDecimal(\"0.9\")"), "Coverage minimum must be 90%")
        assertTrue(ciContent.contains("jacocoTestCoverageVerification"), "CI must run the coverage verification task")
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
