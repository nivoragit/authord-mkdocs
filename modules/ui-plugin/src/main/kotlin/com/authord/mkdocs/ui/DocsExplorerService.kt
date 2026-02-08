package com.authord.mkdocs.ui

import java.io.File

class DocsExplorerService {
    fun discoverMarkdownFiles(projectPath: String, docsRoot: String = "docs"): List<String> {
        val docsDirectory = File(projectPath, docsRoot)
        if (!docsDirectory.exists() || !docsDirectory.isDirectory) {
            return emptyList()
        }

        return docsDirectory
            .walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { it.relativeTo(File(projectPath)).invariantSeparatorsPath }
            .sorted()
            .toList()
    }
}
