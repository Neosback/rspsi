package com.rspsi.server;

/** Optional capabilities supplied by a server/project adapter. */
public enum ServerCapability {
    PROJECT_LAYOUT,
    GRADLE_PROJECT_MODEL,
    CACHE_DISCOVERY,
    BUILD_CACHE,
    FRESH_CACHE,
    CLEAN_CS2,
    MERGE_GAMEVALS,
    PACK_MODULES,
    CONTENT_INVENTORY,
    GAMEVALS,
    SERVER_CACHE,
    SOURCE_STAGING,
    SERVER_LAUNCH,
    RUNTIME_BRIDGE
}
