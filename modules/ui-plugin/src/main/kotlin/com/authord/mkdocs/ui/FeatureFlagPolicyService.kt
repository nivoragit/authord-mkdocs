package com.authord.mkdocs.ui

import com.authord.mkdocs.core.flags.FeatureFlagPolicy

class FeatureFlagPolicyService(
    private var currentPolicy: FeatureFlagPolicy = FeatureFlagPolicy(),
) {
    fun current(): FeatureFlagPolicy = currentPolicy

    fun update(policy: FeatureFlagPolicy) {
        currentPolicy = policy
    }

    fun allowsMvpOnlyExecution(): Boolean {
        return currentPolicy.allowsMvpFlow() && currentPolicy.disallowsFutureCycleFeatures()
    }
}
