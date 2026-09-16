package com.rspsi.project;

import com.rspsi.cache.OsrsCacheMetadata;

import java.util.Objects;

/** Stable identity for an RSPSi project and the cache it was authored against. */
public record ProjectMetadata(
        int formatVersion,
        String game,
        int cacheRevision,
        Integer cacheSubRevision,
        String cacheFingerprint
) {
    public static final int CURRENT_FORMAT_VERSION = 1;
    public static final String OSRS_GAME = "oldschool";

    public ProjectMetadata {
        if (formatVersion <= 0) {
            throw new IllegalArgumentException("Project format version must be positive");
        }
        game = Objects.requireNonNull(game, "game").trim();
        if (!OSRS_GAME.equals(game)) {
            throw new IllegalArgumentException("RSPSi projects must target oldschool");
        }
        if (cacheRevision <= 0) {
            throw new IllegalArgumentException("Project cache revision must be positive");
        }
        if (cacheSubRevision != null && cacheSubRevision < 0) {
            throw new IllegalArgumentException("Project cache subrevision cannot be negative");
        }
        cacheFingerprint = Objects.requireNonNull(cacheFingerprint, "cacheFingerprint").trim();
        if (cacheFingerprint.isEmpty()) {
            throw new IllegalArgumentException("Project cache fingerprint cannot be empty");
        }
    }

    public static ProjectMetadata forCache(OsrsCacheMetadata cache) {
        Objects.requireNonNull(cache, "cache");
        return new ProjectMetadata(
                CURRENT_FORMAT_VERSION,
                OSRS_GAME,
                cache.revision(),
                cache.subRevision(),
                cache.fingerprint());
    }

    public boolean matches(OsrsCacheMetadata cache) {
        Objects.requireNonNull(cache, "cache");
        return cacheRevision == cache.revision()
                && Objects.equals(cacheSubRevision, cache.subRevision())
                && cacheFingerprint.equals(cache.fingerprint());
    }
}
