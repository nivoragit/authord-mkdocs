package com.authord.mkdocs.core.runtime

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
