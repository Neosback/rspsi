package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;

import java.util.Objects;

/** Displee adapter retained for 317 and custom/legacy cache compatibility. */
public final class LegacyDispleeCacheStore implements CacheStore {

    private final CacheLibrary library;

    public LegacyDispleeCacheStore(CacheLibrary library) {
        this.library = Objects.requireNonNull(library, "library");
    }

    @Override
    public byte[] read(int index, int archive, int file) {
        try {
            Index cacheIndex = library.index(index);
            if (cacheIndex == null || cacheIndex.archive(archive) == null
                    || cacheIndex.archive(archive).file(file) == null) {
                return null;
            }
            return cacheIndex.archive(archive).file(file).getData();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void write(int index, int archive, int file, byte[] data) {
        Objects.requireNonNull(data, "data");
        library.index(index).archive(archive).add(file, data);
    }

    @Override
    public void flush() {
        // Displee writes update the in-memory cache and are persisted on close.
    }

    @Override
    public void close() {
        library.close();
    }
}
