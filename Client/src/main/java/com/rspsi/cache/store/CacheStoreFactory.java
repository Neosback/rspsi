package com.rspsi.cache.store;

import dev.openrune.filesystem.Cache;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit backend construction for legacy and modern OSRS cache paths. */
public final class CacheStoreFactory {

    private CacheStoreFactory() {
    }

    /**
     * Opens the legacy cache backend from a path without exposing Displee to
     * the caller. This remains a compatibility backend; OSRS production
     * callers should prefer {@link #openRune(Path)}.
     */
    public static CacheStore legacy(Path path) {
        Objects.requireNonNull(path, "path");
        return LegacyDispleeCacheStore.open(path);
    }

    public static CacheStore openRune(Path path) {
        return OpenRuneCacheStore.open(path);
    }

    /**
     * Opens the canonical production backend for an OSRS cache.
     *
     * <p>This named entry point is intentionally separate from {@link #legacy(Path)}.
     * Callers selecting an OSRS project should use this method so a modern
     * cache cannot silently fall back to the 317/Displee compatibility path.</p>
     */
    public static OpenRuneCacheStore openOsrs(Path path) {
        return OpenRuneCacheStore.open(path);
    }

    /** Opens an explicitly selected writable OpenRune output cache. */
    public static CacheStore openRuneWritable(Path path) {
        return OpenRuneCacheStore.openWritable(path);
    }

    /** Creates a staged store whose writes commit only to the supplied output backend. */
    public static CacheStore layered(CacheStore base, CacheStore output) {
        return new LayeredCacheStore(base, output);
    }

    static CacheStore openRune(Cache cache) {
        return new OpenRuneCacheStore(cache);
    }
}
