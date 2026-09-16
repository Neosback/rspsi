package com.rspsi.cache.store;

import com.rspsi.cache.CacheStoreCapabilities;

/**
 * Neutral byte-oriented cache boundary used by editor code.
 *
 * Implementations may target different cache layouts. Callers must not depend
 * on the implementation library or its archive/index types.
 */
public interface CacheStore extends AutoCloseable {

    byte[] read(int index, int archive, int file);

    /**
     * Resolves a named archive without exposing the cache implementation's
     * archive object. Backends that do not support named archives return -1.
     */
    default int archiveId(int index, String archiveName) {
        return -1;
    }

    /** Returns numeric archive IDs when a backend exposes them without names. */
    default int[] archiveIds(int index) {
        return new int[0];
    }

    void write(int index, int archive, int file, byte[] data);

    void flush();

    /** Describes backend behavior without exposing its implementation library. */
    default CacheStoreCapabilities capabilities() {
        return new CacheStoreCapabilities(false, false, false);
    }

    @Override
    default void close() {
        flush();
    }
}
