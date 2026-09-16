package com.rspsi.cache.store;

/**
 * Neutral byte-oriented cache boundary used by editor code.
 *
 * Implementations may target different cache layouts. Callers must not depend
 * on the implementation library or its archive/index types.
 */
public interface CacheStore extends AutoCloseable {

    byte[] read(int index, int archive, int file);

    void write(int index, int archive, int file, byte[] data);

    void flush();

    @Override
    default void close() {
        flush();
    }
}
