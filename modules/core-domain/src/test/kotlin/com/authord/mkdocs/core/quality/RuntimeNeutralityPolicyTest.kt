package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class RuntimeNeutralityPolicyTest {
    @Test
    fun `main sources avoid shell activation and hardcoded python paths`() {
        val violations = collectForbiddenPatternViolations(
            forbiddenPatterns = listOf(
                ".venv/bin/",
                ".venv/bin/activate",
                "venv/bin/python",
                "/bin/python",
                "python.exe",
                "Scripts/python",
                "Scripts\\python",
                "Scripts/activate",
            ),
        )

        assertTrue(
            violations.isEmpty(),
            "Main sources contain forbidden runtime bootstrap patterns:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `main sources avoid hardcoded runtime host and port defaults`() {
        val violations = collectForbiddenPatternViolations(
            forbiddenPatterns = listOf(
                "--dev-addr",
                "127.0.0.1:",
                "localhost:",
                ":8000",
                ":8080",
            ),
        )

        assertTrue(
            violations.isEmpty(),
            "Main sources contain forbidden hardcoded runtime endpoint patterns:\n${violations.joinToString("\n")}",
        )
    }

    private fun collectForbiddenPatternViolations(forbiddenPatterns: List<String>): List<String> {
        val root = findRepoRoot()
        val modulesRoot = root.resolve("modules")
        val violations = mutableListOf<String>()

        Files.walk(modulesRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().replace('\\', '/').contains("/src/main/kotlin/") }
                .filter { it.fileName.toString().endsWith(".kt") }
                .forEach { sourceFile ->
                    val content = sourceFile.readText()
                    forbiddenPatterns.forEach { pattern ->
                        if (content.contains(pattern)) {
                            violations += "${root.relativize(sourceFile)} -> \"$pattern\""
                        }
                    }
                }
        }

        return violations
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
