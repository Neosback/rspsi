package com.rspsi.cache.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Immutable status snapshot exposed to frontends while a cache loads. */
public record CacheSessionStatus(
        CacheSessionState state,
        Path requestedPath,
        LoadedOsrsCacheSession current,
        String message,
        Throwable failure
) {
    public CacheSessionStatus {
        state = Objects.requireNonNull(state, "state");
        message = message == null ? "" : message.trim();
    }

    public Optional<LoadedOsrsCacheSession> currentSession() {
        return Optional.ofNullable(current);
    }
}
