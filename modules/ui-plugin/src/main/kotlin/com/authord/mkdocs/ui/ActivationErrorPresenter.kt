package com.authord.mkdocs.ui

enum class ActivationFailureReason {
    MVP_DISABLED,
    BOOTSTRAP_FAILED,
    START_FAILED,
    BASE_URL_NOT_FOUND,
}

class ActivationErrorPresenter {
    fun present(reason: ActivationFailureReason, details: String = ""): String {
        val base = when (reason) {
            ActivationFailureReason.MVP_DISABLED -> "MVP activation is disabled by feature flags."
            ActivationFailureReason.BOOTSTRAP_FAILED -> "Runtime bootstrap failed."
            ActivationFailureReason.START_FAILED -> "Preview server failed to start."
            ActivationFailureReason.BASE_URL_NOT_FOUND -> "Could not detect preview URL from server output."
        }

        return if (details.isBlank()) base else "$base Details: $details"
    }
}
