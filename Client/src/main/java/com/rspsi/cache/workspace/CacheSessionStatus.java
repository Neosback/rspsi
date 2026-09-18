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
        Throwable failure,
        CacheLoadPhase phase,
        double progress
) {
    public CacheSessionStatus(CacheSessionState state, Path requestedPath,
                              LoadedOsrsCacheSession current, String message,
                              Throwable failure) {
        this(state, requestedPath, current, message, failure,
                defaultPhase(state), defaultProgress(state));
    }

    public CacheSessionStatus {
        state = Objects.requireNonNull(state, "state");
        message = message == null ? "" : message.trim();
        phase = Objects.requireNonNull(phase, "cache load phase");
        if (!Double.isFinite(progress) || progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("Cache load progress must be between 0 and 1");
        }
    }

    public Optional<LoadedOsrsCacheSession> currentSession() {
        return Optional.ofNullable(current);
    }

    private static CacheLoadPhase defaultPhase(CacheSessionState state) {
        return switch (state) {
            case EMPTY -> CacheLoadPhase.IDLE;
            case LOADING -> CacheLoadPhase.VALIDATING;
            case READY -> CacheLoadPhase.READY;
            case FAILED -> CacheLoadPhase.FAILED;
        };
    }

    private static double defaultProgress(CacheSessionState state) {
        return switch (state) {
            case EMPTY -> 0.0;
            case LOADING -> 0.1;
            case READY, FAILED -> 1.0;
        };
    }
}
