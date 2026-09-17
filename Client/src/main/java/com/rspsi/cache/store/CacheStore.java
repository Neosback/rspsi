package com.rspsi.cache.store;

import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.OsrsCacheMetadata;

import java.util.Optional;

/**
 * Neutral byte-oriented cache boundary used by editor code.
 *
 * Implementations may target different cache layouts. Callers must not depend
 * on the implementation library or its archive/index types.
 */
public interface CacheStore extends AutoCloseable {

    /**
     * Reads a file as an owned byte array. Implementations must not return a
     * mutable array backed by their cache library.
     */
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

    /** Returns file IDs exposed by one archive without leaking cache types. */
    default int[] fileIds(int index, int archive) {
        return new int[0];
    }

    /** Writes a file without retaining the caller's mutable array. */
    void write(int index, int archive, int file, byte[] data);

    void flush();

    /** Describes backend behavior without exposing its implementation library. */
    default CacheStoreCapabilities capabilities() {
        return new CacheStoreCapabilities(false, false, false);
    }

    /** Returns OSRS cache identity when this backend can provide it. */
    default Optional<OsrsCacheMetadata> metadata(int revision) {
        return Optional.empty();
    }

    @Override
    default void close() {
        flush();
    }
}
