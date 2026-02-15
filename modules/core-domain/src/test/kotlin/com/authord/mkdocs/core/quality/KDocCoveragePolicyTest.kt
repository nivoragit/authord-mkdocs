package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertTrue

class KDocCoveragePolicyTest {
    private val phase2ApiSurfaceFiles = listOf(
        "modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/MkDocsConfigGateway.kt",
        "modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/DocsFileGateway.kt",
        "modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/TreeSyncOrchestrator.kt",
        "modules/extension-ports/src/main/kotlin/com/authord/mkdocs/ports/topic/InstanceRegistryPort.kt",
        "modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeApplicationService.kt",
        "modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeUiService.kt",
        "modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeSyncOrchestratorService.kt",
        "modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/TopicTreeWatcherCoordinator.kt",
        "modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/MkDocsYamlGateway.kt",
    )

    @Test
    fun `phase2 public api surface declarations include KDoc`() {
        val root = findRepoRoot()
        val violations = mutableListOf<String>()

        phase2ApiSurfaceFiles.forEach { relativePath ->
            val sourceFile = root.resolve(relativePath)
            assertTrue(Files.exists(sourceFile), "Expected API surface file not found: $relativePath")
            checkFileForMissingKDoc(root, sourceFile, violations)
        }

        assertTrue(
            violations.isEmpty(),
            "Missing KDoc for public declarations:\n${violations.joinToString("\n")}",
        )
    }

    private fun checkFileForMissingKDoc(root: Path, sourceFile: Path, violations: MutableList<String>) {
        val lines = sourceFile.readLines()
        var braceDepth = 0
        lines.forEachIndexed { zeroIndex, rawLine ->
            val lineIndex = zeroIndex + 1
            val line = rawLine.trimStart()
            if (isPublicDeclaration(line, braceDepth) && !hasKDoc(lines, lineIndex)) {
                violations += "${root.relativize(sourceFile)}:$lineIndex -> $line"
            }
            braceDepth = updateBraceDepth(braceDepth, rawLine)
        }
    }

    private fun isPublicDeclaration(line: String, braceDepth: Int): Boolean {
        if (line.startsWith("private ") || line.startsWith("internal ")) return false
        if (line.startsWith("package ") || line.startsWith("import ")) return false
        // Public API declarations can appear at file scope (0) and class/interface scope (1).
        if (braceDepth > 1) return false

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

    private fun updateBraceDepth(current: Int, rawLine: String): Int {
        val opens = rawLine.count { it == '{' }
        val closes = rawLine.count { it == '}' }
        return (current + opens - closes).coerceAtLeast(0)
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
