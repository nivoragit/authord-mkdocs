package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy

/**
 * Maintains effective feature-flag policy for plugin orchestration.
 */
class FeatureFlagPolicyService(
    private var currentPolicy: FeatureFlagPolicy = FeatureFlagPolicy(),
) {
    /** Returns current policy snapshot. */
    fun current(): FeatureFlagPolicy = currentPolicy

    /** Replaces active policy with provided value. */
    fun update(policy: FeatureFlagPolicy) {
        currentPolicy = policy
    }

    /** Returns `true` when only MVP behavior is allowed by current flags. */
    fun allowsMvpOnlyExecution(): Boolean {
        return currentPolicy.allowsMvpFlow() && currentPolicy.disallowsFutureCycleFeatures()
    }
}
