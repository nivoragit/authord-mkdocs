package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertTrue

class KDocCoveragePolicyTest {
    @Test
    fun `public declarations in main kotlin sources include KDoc`() {
        val root = findRepoRoot()
        val modulesRoot = root.resolve("modules")
        val violations = mutableListOf<String>()

        Files.walk(modulesRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().replace('\\', '/').contains("/src/main/kotlin/") }
                .filter { it.fileName.toString().endsWith(".kt") }
                .forEach { sourceFile ->
                    checkFileForMissingKDoc(root, sourceFile, violations)
                }
        }

        assertTrue(
            violations.isEmpty(),
            "Missing KDoc for public declarations:\n${violations.joinToString("\n")}",
        )
    }

    private fun checkFileForMissingKDoc(root: Path, sourceFile: Path, violations: MutableList<String>) {
        val lines = sourceFile.readLines()
        lines.forEachIndexed { zeroIndex, rawLine ->
            val lineIndex = zeroIndex + 1
            val line = rawLine.trimStart()
            if (!isPublicDeclaration(line)) return@forEachIndexed
            if (hasKDoc(lines, lineIndex)) return@forEachIndexed

            violations += "${root.relativize(sourceFile)}:$lineIndex -> $line"
        }
    }

    private fun isPublicDeclaration(line: String): Boolean {
        if (line.startsWith("private ") || line.startsWith("internal ")) return false

        val isTypeDeclaration = line.matches(
            Regex("^(data class|class|interface|fun interface|enum class|object|sealed interface|sealed class|typealias)\\b.*"),
        )
        if (isTypeDeclaration) return true

        val isFunctionDeclaration = line.matches(
            Regex("^(override\\s+)?(suspend\\s+)?fun\\s+[A-Za-z_][A-Za-z0-9_]*\\b.*"),
        )
        if (!isFunctionDeclaration) return false

        return !line.contains(" private ") && !line.contains(" internal ")
    }

    private fun hasKDoc(lines: List<String>, declarationLine: Int): Boolean {
        var index = declarationLine - 2
        while (index >= 0 && lines[index].isBlank()) {
            index--
        }
        while (index >= 0 && lines[index].trimStart().startsWith("@")) {
            index--
            while (index >= 0 && lines[index].isBlank()) {
                index--
            }
        }
        if (index < 0) return false

        val current = lines[index].trimStart()
        if (current.startsWith("/**") && current.endsWith("*/")) {
            return true
        }
        if (!current.startsWith("*/")) {
            return false
        }

        index--
        while (index >= 0) {
            val line = lines[index].trimStart()
            if (line.startsWith("/**")) return true
            if (!line.startsWith("*") && !line.isBlank()) return false
            index--
        }
        return false
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
