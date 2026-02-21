package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.core.topic.PathPolicy

/**
 * Filesystem event kinds used by startup reconciliation watcher logic.
 */
enum class WatcherEventKind {
    CREATE,
    UPDATE,
    DELETE,
    RENAME,
    MOVE,
}

/**
 * One file-change event observed by topic-tree watcher logic.
 */
data class TopicTreeFileChange(
    val kind: WatcherEventKind,
    val path: String,
    val newPath: String? = null,
)

/**
 * Non-destructive follow-up action selected for watcher-triggered reconciliation.
 */
enum class WatcherFollowUp {
    NONE,
    REPORT_BROKEN_REFERENCE,
    ADD_UNLINKED_BUCKET_ENTRY,
    IGNORE_EXTERNAL,
}

/**
 * Decision returned by watcher trigger evaluation.
 */
data class WatcherDecision(
    val triggerReconciliation: Boolean,
    val followUp: WatcherFollowUp,
    val destructiveAction: Boolean,
    val reason: String,
)

/**
 * Evaluates file-change events against the explicit startup reconciliation trigger matrix.
 */
class TopicTreeWatcherCoordinator(
    projectRootPath: String,
    docsDirPath: String,
    configPaths: Set<String>,
    private val pathPolicy: PathPolicy = PathPolicy.forCurrentOs(),
) {
    private val projectRoot = pathPolicy.normalize(projectRootPath).trimEnd('/')
    private val docsDir = pathPolicy.normalize(docsDirPath).trimEnd('/')
    private val configPathKeys = configPaths
        .map { pathPolicy.comparisonKey(pathPolicy.normalize(it).trimEnd('/')) }
        .toSet()

    /**
     * Resolves whether a file change should trigger reconciliation.
     */
    fun evaluate(event: TopicTreeFileChange): WatcherDecision {
        return when (event.kind) {
            WatcherEventKind.RENAME,
            WatcherEventKind.MOVE,
            -> evaluateMoveLikeEvent(event)

            WatcherEventKind.CREATE,
            WatcherEventKind.UPDATE,
            WatcherEventKind.DELETE,
            -> evaluateSinglePathEvent(event)
        }
    }

    private fun evaluateSinglePathEvent(event: TopicTreeFileChange): WatcherDecision {
        val path = pathPolicy.normalize(event.path)
        // Guard against external noise first: events fully outside project scope are never allowed
        // to drive reconciliation side effects.
        if (!isWithinProject(path)) {
            return WatcherDecision(
                triggerReconciliation = false,
                followUp = WatcherFollowUp.IGNORE_EXTERNAL,
                destructiveAction = false,
                reason = "Path is outside project root",
            )
        }

        if (isConfigPath(path)) {
            return WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.NONE,
                destructiveAction = false,
                reason = "Configuration lifecycle change",
            )
        }

        if (isMarkdownInDocsDir(path)) {
            return WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.NONE,
                destructiveAction = false,
                reason = "docs_dir markdown lifecycle change",
            )
        }

        return WatcherDecision(
            triggerReconciliation = false,
            followUp = WatcherFollowUp.NONE,
            destructiveAction = false,
            reason = "Event not in reconciliation trigger matrix",
        )
    }

    private fun evaluateMoveLikeEvent(event: TopicTreeFileChange): WatcherDecision {
        val fromPath = pathPolicy.normalize(event.path)
        val toPath = pathPolicy.normalize(event.newPath ?: event.path)

        if (!isWithinProject(fromPath) && !isWithinProject(toPath)) {
            return WatcherDecision(
                triggerReconciliation = false,
                followUp = WatcherFollowUp.IGNORE_EXTERNAL,
                destructiveAction = false,
                reason = "Move/rename is fully external to project",
            )
        }

        if (isConfigPath(fromPath) || isConfigPath(toPath)) {
            return WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.NONE,
                destructiveAction = false,
                reason = "Configuration rename/move",
            )
        }

        val fromInDocs = isMarkdownInDocsDir(fromPath)
        val toInDocs = isMarkdownInDocsDir(toPath)

        return when {
            fromInDocs && toInDocs -> WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.NONE,
                destructiveAction = false,
                reason = "docs_dir markdown rename/move",
            )

            fromInDocs && !toInDocs -> WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.REPORT_BROKEN_REFERENCE,
                destructiveAction = false,
                // Moving out of docs_dir is treated as non-destructive divergence: report and keep
                // nav untouched until an explicit user mutation resolves it.
                reason = "Moved outside docs_dir; report broken nav path",
            )

            !fromInDocs && toInDocs -> WatcherDecision(
                triggerReconciliation = true,
                followUp = WatcherFollowUp.ADD_UNLINKED_BUCKET_ENTRY,
                destructiveAction = false,
                // Moving into docs_dir is never auto-linked; reconciliation exposes it as unlinked
                // so users opt into canonical-nav changes explicitly.
                reason = "Moved into docs_dir; add unlinked bucket entry",
            )

            else -> WatcherDecision(
                triggerReconciliation = false,
                followUp = WatcherFollowUp.NONE,
                destructiveAction = false,
                reason = "Non-markdown rename/move outside docs_dir",
            )
        }
    }

    private fun isWithinProject(path: String): Boolean {
        return pathPolicy.equivalent(path, projectRoot) || path.startsWith("$projectRoot/")
    }

    private fun isConfigPath(path: String): Boolean {
        return configPathKeys.contains(pathPolicy.comparisonKey(path.trimEnd('/')))
    }

    private fun isMarkdownInDocsDir(path: String): Boolean {
        return path.endsWith(".md") && (pathPolicy.equivalent(path, docsDir) || path.startsWith("$docsDir/"))
    }
}
