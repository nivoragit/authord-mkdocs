package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * Runs a long-running operation as a background task with IDE progress indicator support.
 */
fun <T> runAuthordBackgroundTask(
    project: Project,
    title: String,
    canBeCancelled: Boolean = false,
    operation: (ProgressIndicator) -> T,
    onSuccess: (T) -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, canBeCancelled) {
        private var hasResult: Boolean = false
        private var result: T? = null

        override fun run(indicator: ProgressIndicator) {
            result = operation(indicator)
            hasResult = true
        }

        override fun onSuccess() {
            if (!hasResult) {
                return
            }
            @Suppress("UNCHECKED_CAST")
            onSuccess(result as T)
        }

        override fun onThrowable(error: Throwable) {
            onError(error)
        }
    })
}
