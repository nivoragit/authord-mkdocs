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

        val content = Files.readString(buildFile)

        assertTrue(content.contains("jacocoTestCoverageVerification"), "Coverage verification task must be configured")
        assertTrue(content.contains("minimum = BigDecimal(\"1.0\")"), "Coverage minimum must be 100%")
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
