package com.rspsi.project;

/**
 * Persisted project integration permissions.
 *
 * <p>Destructive reset/bootstrap actions remain explicit per operation even for DEVELOPER-like
 * projects; FRESH_CACHE_RESET is never implied by another capability.</p>
 */
public enum ProjectIntegrationCapability {
    PROJECT_READ,
    PROJECT_SOURCE_WRITE,
    CACHE_BUILD,
    GAMEVAL_BUILD,
    CS2_BUILD,
    SERVER_LAUNCH,
    EXTERNAL_COMMAND,
    FRESH_CACHE_RESET
}
