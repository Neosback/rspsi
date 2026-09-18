package com.rspsi.cache.workspace;

/** Lifecycle state for the application-level selected OSRS cache. */
public enum CacheSessionState {
    EMPTY,
    LOADING,
    READY,
    FAILED
}
