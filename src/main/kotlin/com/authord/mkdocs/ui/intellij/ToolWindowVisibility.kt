package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal const val AUTHORD_TREEVIEW_TOOL_WINDOW_ID: String = "Authord"
internal const val AUTHORD_LEGACY_TOOL_WINDOW_ID: String = "Authord MkDocs"

internal fun showAuthordToolWindow(project: Project) {
    val manager = ToolWindowManager.getInstance(project)
    val toolWindow = manager.getToolWindow(AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
        ?: manager.getToolWindow(AUTHORD_LEGACY_TOOL_WINDOW_ID)
        ?: return
    if (!toolWindow.isVisible) {
        toolWindow.show(null)
    }
    toolWindow.activate(null)
}

internal fun toggleAuthordToolWindow(project: Project) {
    val manager = ToolWindowManager.getInstance(project)
    val toolWindow = manager.getToolWindow(AUTHORD_TREEVIEW_TOOL_WINDOW_ID)
        ?: manager.getToolWindow(AUTHORD_LEGACY_TOOL_WINDOW_ID)
        ?: return
    if (toolWindow.isVisible) {
        toolWindow.hide(null)
        return
    }
    toolWindow.show(null)
    toolWindow.activate(null)
}
