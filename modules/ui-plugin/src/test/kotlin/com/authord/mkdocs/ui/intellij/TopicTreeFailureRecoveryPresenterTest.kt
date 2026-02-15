package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.DefaultTopicSyncError
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import kotlin.test.Test
import kotlin.test.assertTrue

class TopicTreeFailureRecoveryPresenterTest {
    private val presenter = TopicTreeFailureRecoveryPresenter()

    @Test
    fun `validation failure guidance states no changes applied`() {
        val message = presenter.present(
            operationLabel = "Rename topic",
            error = DefaultTopicSyncError(TopicSyncErrorCode.VALIDATION, "Duplicate title"),
        )

        assertTrue(message.nonDestructive)
        assertTrue(message.summary.contains("rejected", ignoreCase = true))
        assertTrue(message.guidance.contains("No changes were applied", ignoreCase = true))
        assertTrue(message.guidance.contains("Duplicate title"))
    }

    @Test
    fun `orchestration failure guidance states rollback compensation`() {
        val message = presenter.present(
            operationLabel = "Move topic",
            error = DefaultTopicSyncError(TopicSyncErrorCode.ORCHESTRATION, "Compensation completed"),
        )

        assertTrue(message.nonDestructive)
        assertTrue(message.summary.contains("failed safely", ignoreCase = true))
        assertTrue(message.guidance.contains("Rollback/compensation", ignoreCase = true))
    }
}
