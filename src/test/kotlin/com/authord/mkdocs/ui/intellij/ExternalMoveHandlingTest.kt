package com.authord.mkdocs.ui.intellij

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalMoveHandlingTest {
    private val coordinator = TopicTreeWatcherCoordinator(
        projectRootPath = "/repo",
        docsDirPath = "/repo/docs",
        configPaths = setOf("/repo/mkdocs.yml"),
    )

    @Test
    fun `inside docs_dir moved outside triggers broken reference validation without destructive action`() {
        val decision = coordinator.evaluate(
            TopicTreeFileChange(
                kind = WatcherEventKind.MOVE,
                path = "/repo/docs/guide/install.md",
                newPath = "/repo/archive/install.md",
            ),
        )

        assertTrue(decision.triggerReconciliation)
        assertEquals(WatcherFollowUp.REPORT_BROKEN_REFERENCE, decision.followUp)
        assertFalse(decision.destructiveAction)
    }

    @Test
    fun `outside docs_dir moved inside creates unlinked file follow-up`() {
        val decision = coordinator.evaluate(
            TopicTreeFileChange(
                kind = WatcherEventKind.MOVE,
                path = "/repo/tmp/new-topic.md",
                newPath = "/repo/docs/new-topic.md",
            ),
        )

        assertTrue(decision.triggerReconciliation)
        assertEquals(WatcherFollowUp.ADD_UNLINKED_BUCKET_ENTRY, decision.followUp)
        assertFalse(decision.destructiveAction)
    }

    @Test
    fun `rename or move wholly outside project root is ignored`() {
        val decision = coordinator.evaluate(
            TopicTreeFileChange(
                kind = WatcherEventKind.RENAME,
                path = "/outside/a.md",
                newPath = "/outside/b.md",
            ),
        )

        assertFalse(decision.triggerReconciliation)
        assertEquals(WatcherFollowUp.IGNORE_EXTERNAL, decision.followUp)
    }
}
