package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopicTreeWatcherTriggerMatrixTest {
    private val coordinator = TopicTreeWatcherCoordinator(
        projectRootPath = "/repo",
        docsDirPath = "/repo/docs",
        configPaths = setOf("/repo/mkdocs.yml", "/repo/mkdocs.yaml"),
    )

    @Test
    fun `triggers reconciliation on mkdocs config file lifecycle changes`() {
        val events = listOf(
            TopicTreeFileChange(WatcherEventKind.CREATE, path = "/repo/mkdocs.yml"),
            TopicTreeFileChange(WatcherEventKind.UPDATE, path = "/repo/mkdocs.yaml"),
            TopicTreeFileChange(WatcherEventKind.DELETE, path = "/repo/mkdocs.yml"),
            TopicTreeFileChange(WatcherEventKind.RENAME, path = "/repo/mkdocs.yml", newPath = "/repo/mkdocs-new.yml"),
            TopicTreeFileChange(WatcherEventKind.MOVE, path = "/repo/mkdocs.yml", newPath = "/repo/config/mkdocs.yml"),
        )

        events.forEach { event ->
            val decision = coordinator.evaluate(event)
            assertTrue(decision.triggerReconciliation, "Expected trigger for event: $event")
        }
    }

    @Test
    fun `triggers reconciliation on markdown changes within docs_dir only`() {
        val shouldTrigger = listOf(
            TopicTreeFileChange(WatcherEventKind.CREATE, path = "/repo/docs/new.md"),
            TopicTreeFileChange(WatcherEventKind.UPDATE, path = "/repo/docs/edit.md"),
            TopicTreeFileChange(WatcherEventKind.DELETE, path = "/repo/docs/remove.md"),
            TopicTreeFileChange(WatcherEventKind.RENAME, path = "/repo/docs/old.md", newPath = "/repo/docs/new.md"),
            TopicTreeFileChange(WatcherEventKind.MOVE, path = "/repo/docs/a.md", newPath = "/repo/docs/sub/a.md"),
        )
        shouldTrigger.forEach {
            assertTrue(coordinator.evaluate(it).triggerReconciliation, "Expected trigger for $it")
        }

        val shouldIgnore = listOf(
            TopicTreeFileChange(WatcherEventKind.UPDATE, path = "/repo/docs/logo.png"),
            TopicTreeFileChange(WatcherEventKind.UPDATE, path = "/repo/README.md"),
            TopicTreeFileChange(WatcherEventKind.MOVE, path = "/outside/docs/a.md", newPath = "/outside/docs/b.md"),
        )
        shouldIgnore.forEach {
            assertFalse(coordinator.evaluate(it).triggerReconciliation, "Expected ignore for $it")
        }
    }
}
