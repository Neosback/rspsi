package com.rspsi.cache.store;

import dev.openrune.filesystem.Cache;
import com.rspsi.cache.CacheStoreCapabilities;

import java.nio.file.Path;
import java.util.Objects;

/**
 * OpenRune FileStore compatibility adapter.
 *
 * The first spike intentionally supports reads only. The OpenRune file-backed
 * implementation is read-only, so silently pretending that writes succeeded
 * would risk corrupting edited maps.
 */
public final class OpenRuneCacheStore implements CacheStore {

    private final Cache cache;

    OpenRuneCacheStore(Cache cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public static OpenRuneCacheStore open(Path path) {
        Objects.requireNonNull(path, "path");
        return new OpenRuneCacheStore(Cache.Companion.load(path));
    }

    @Override
    public byte[] read(int index, int archive, int file) {
        return cache.data(index, archive, file, null);
    }

    @Override
    public int archiveId(int index, String archiveName) {
        Objects.requireNonNull(archiveName, "archiveName");
        return cache.archiveId(index, archiveName);
    }

    @Override
    public void write(int index, int archive, int file, byte[] data) {
        throw new UnsupportedOperationException(
                "OpenRune compatibility store is read-only until writable packing is validated");
    }

    @Override
    public void flush() {
        // No writes are accepted by this read-only spike.
    }

    @Override
    public CacheStoreCapabilities capabilities() {
        return new CacheStoreCapabilities(false, true, false);
    }

    @Override
    public void close() {
        cache.close();
    }
}
