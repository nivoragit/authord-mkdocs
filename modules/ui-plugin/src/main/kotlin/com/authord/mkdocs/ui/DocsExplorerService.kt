package com.authord.mkdocs.ui

import java.nio.file.Files
import java.nio.file.Path

/**
 * Discovers documentation markdown files under a docs root.
 */
class DocsExplorerService {
    /**
     * Returns project-relative markdown file paths under `docsRoot`.
     *
     * @param projectPath root project path.
     * @param docsRoot docs folder relative path.
     */
    fun discoverMarkdownFiles(projectPath: String, docsRoot: String = "docs"): List<String> {
        val projectRoot = Path.of(projectPath)
        val docsDirectory = projectRoot.resolve(docsRoot).normalize()
        if (!Files.exists(docsDirectory) || !Files.isDirectory(docsDirectory)) {
            return emptyList()
        }

        return Files.walk(docsDirectory).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
                .map { projectRoot.relativize(it).toString().replace("\\", "/") }
                .sorted()
                .toList()
        }
    }
}
