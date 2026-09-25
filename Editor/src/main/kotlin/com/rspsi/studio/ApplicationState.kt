package com.rspsi.studio

/** Top-level application lifecycle. Workspace state exists only inside [PROJECT_OPEN]. */
enum class ApplicationState {
    PROJECT_LAUNCHER,
    PROJECT_LOADING,
    PROJECT_OPEN,
}
