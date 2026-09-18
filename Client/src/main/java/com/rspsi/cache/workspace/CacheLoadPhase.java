package com.rspsi.cache.workspace;

/** Coarse-grained progress phases exposed by the application cache service. */
public enum CacheLoadPhase {
    IDLE,
    VALIDATING,
    OPENING_FILESYSTEM,
    PREPARING_ASSETS,
    READY,
    FAILED
}
