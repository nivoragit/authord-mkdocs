package com.authord.mkdocs.core.runtime

/**
 * Lifecycle states for runtime setup and serving orchestration.
 */
enum class RuntimeLifecycleState {
    UNINITIALIZED,
    BOOTSTRAPPING,
    READY,
    SERVING,
    RESTARTING,
    STOPPING,
    STOPPED,
    FAILED,
    DISPOSED
}
