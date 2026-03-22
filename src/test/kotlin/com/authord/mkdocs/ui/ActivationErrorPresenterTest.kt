package com.authord.mkdocs.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ActivationErrorPresenterTest {
    private val presenter = ActivationErrorPresenter()

    @Test
    fun `renders message per failure reason`() {
        assertTrue(presenter.present(ActivationFailureReason.MVP_DISABLED).contains("disabled"))
        assertTrue(presenter.present(ActivationFailureReason.BOOTSTRAP_FAILED).contains("bootstrap"))
        assertTrue(presenter.present(ActivationFailureReason.START_FAILED).contains("failed to start"))
        assertTrue(presenter.present(ActivationFailureReason.BASE_URL_NOT_FOUND).contains("detect preview URL"))
    }

    @Test
    fun `includes details when provided`() {
        val message = presenter.present(ActivationFailureReason.BOOTSTRAP_FAILED, "uv missing")

        assertTrue(message.contains("uv missing"))
    }
}
