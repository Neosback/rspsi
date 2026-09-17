package com.rspsi.cache.store;

import com.displee.cache.CacheLibrary;
import com.displee.cache.index.Index;
import com.rspsi.cache.CacheStoreCapabilities;
import com.rspsi.cache.CacheWriteMode;

import java.util.Objects;

/** Displee adapter retained for 317 and custom/legacy cache compatibility. */
public final class LegacyDispleeCacheStore implements CacheStore {

    private final CacheLibrary library;
    private boolean closed;

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
    public int archiveId(int index, String archiveName) {
        Objects.requireNonNull(archiveName, "archiveName");
        try {
            Index cacheIndex = library.index(index);
            return cacheIndex == null ? -1 : cacheIndex.archiveId(archiveName);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    @Override
    public int[] archiveIds(int index) {
        try {
            Index cacheIndex = library.index(index);
            return cacheIndex == null ? new int[0] : cacheIndex.archiveIds();
        } catch (RuntimeException ignored) {
            return new int[0];
        }
    }

    @Override
    public int[] fileIds(int index, int archive) {
        try {
            Index cacheIndex = library.index(index);
            return cacheIndex == null || cacheIndex.archive(archive) == null
                    ? new int[0] : cacheIndex.archive(archive).fileIds();
        } catch (RuntimeException ignored) {
            return new int[0];
        }
    }

    @Override
    public void write(int index, int archive, int file, byte[] data) {
        Objects.requireNonNull(data, "data");
        library.index(index).archive(archive).add(file, data);
    }

    @Override
    public void flush() {
        // Archive.add(...) only marks the archive dirty. CacheLibrary.close()
        // closes its files without repacking dirty archives, so an explicit
        // update is required to make CacheStore writes durable.
        library.update();
    }

    @Override
    public CacheStoreCapabilities capabilities() {
        // The adapter writes archive/file payloads and explicitly repacks
        // dirty indexes during flush, which is the map-packing capability
        // required by the neutral OSRS map service.
        return new CacheStoreCapabilities(true, false, true, CacheWriteMode.DIRECT);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        flush();
        library.close();
        closed = true;
    }
}
