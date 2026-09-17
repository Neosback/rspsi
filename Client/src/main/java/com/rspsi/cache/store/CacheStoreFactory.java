package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import dev.openrune.filesystem.Cache;

import java.nio.file.Path;
import java.util.Objects;

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

    /** Opens an explicitly selected writable OpenRune output cache. */
    public static CacheStore openRuneWritable(Path path) {
        return OpenRuneCacheStore.openWritable(path);
    }

    /**
     * Opens an OSRS cache read-only while staging writes into an explicit,
     * separately prepared Displee output cache. The paths must differ so a
     * save cannot silently mutate the source cache.
     */
    public static CacheStore openRuneWithDispleeOutput(Path basePath, Path outputPath) {
        Objects.requireNonNull(basePath, "basePath");
        Objects.requireNonNull(outputPath, "outputPath");
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path normalizedOutput = outputPath.toAbsolutePath().normalize();
        if (normalizedBase.equals(normalizedOutput)) {
            throw new IllegalArgumentException("OSRS base and output cache paths must differ");
        }
        CacheStore base = openRune(normalizedBase);
        try {
            return layered(base, new LegacyDispleeCacheStore(
                    new CacheLibrary(normalizedOutput.toString(), false, null)));
        } catch (RuntimeException exception) {
            base.close();
            throw exception;
        }
    }

    /** Creates a staged store whose writes commit only to the supplied output backend. */
    public static CacheStore layered(CacheStore base, CacheStore output) {
        return new LayeredCacheStore(base, output);
    }

    static CacheStore openRune(Cache cache) {
        return new OpenRuneCacheStore(cache);
    }
}
