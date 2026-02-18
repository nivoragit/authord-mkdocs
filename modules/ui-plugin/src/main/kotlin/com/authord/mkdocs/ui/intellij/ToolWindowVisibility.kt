package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal const val AUTHORD_TOOL_WINDOW_ID: String = "Authord MkDocs"

internal fun showAuthordToolWindow(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AUTHORD_TOOL_WINDOW_ID) ?: return
    if (!toolWindow.isVisible) {
        toolWindow.show(null)
    }
    toolWindow.activate(null)
}
