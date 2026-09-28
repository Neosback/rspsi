package com.rspsi.server

/** Overall health of a saved server connection. */
enum class ServerIntegrationStatus {
    SUPPORTED,
    SUPPORTED_WITH_OVERRIDES,
    PARTIAL,
    STALE,
    INCOMPATIBLE,
    NOT_DETECTED,
}
