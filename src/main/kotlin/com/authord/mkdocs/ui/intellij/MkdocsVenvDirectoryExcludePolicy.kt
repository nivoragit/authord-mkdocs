package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import java.nio.file.Path

/**
 * Excludes plugin-managed virtual environments from project indexing.
 */
class MkdocsVenvDirectoryExcludePolicy : DirectoryIndexExcludePolicy {
    override fun getExcludeUrlsForProject(): Array<String> {
        val excludedUrls = linkedSetOf<String>()
        ProjectManager.getInstance().openProjects.forEach { project ->
            val basePath = project.basePath ?: return@forEach
            val venvPath = Path.of(basePath).resolve(".mkdocs-plugin-venv").toAbsolutePath().normalize()
            excludedUrls += VfsUtilCore.pathToUrl(venvPath.toString())
        }
        return excludedUrls.toTypedArray()
    }
}
