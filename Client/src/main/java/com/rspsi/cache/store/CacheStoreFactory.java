package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import dev.openrune.filesystem.Cache;

import java.nio.file.Path;

/** Explicit backend construction; legacy Displee remains the application default. */
public final class CacheStoreFactory {

    private CacheStoreFactory() {
    }

    public static CacheStore legacy(CacheLibrary library) {
        return new LegacyDispleeCacheStore(library);
    }

    public static CacheStore openRune(Path path) {
        return OpenRuneCacheStore.open(path);
    }

    static CacheStore openRune(Cache cache) {
        return new OpenRuneCacheStore(cache);
    }
}
