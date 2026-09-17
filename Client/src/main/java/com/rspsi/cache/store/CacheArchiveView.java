package com.rspsi.cache.store;

import java.util.Objects;

/** Neutral view of one cache archive for compatibility loaders. */
public final class CacheArchiveView {
    private final CacheStore store;
    private final int index;
    private final int archive;

    CacheArchiveView(CacheStore store, int index, int archive) {
        this.store = Objects.requireNonNull(store, "store");
        if (index < 0 || archive < 0) {
            throw new IllegalArgumentException("Cache coordinates cannot be negative");
        }
        this.index = index;
        this.archive = archive;
    }

    public int id() {
        return archive;
    }

    public int[] fileIds() {
        return store.fileIds(index, archive).clone();
    }

    public byte[] file(int file) {
        if (file < 0) throw new IllegalArgumentException("Cache file cannot be negative");
        byte[] data = store.read(index, archive, file);
        return data == null ? null : data.clone();
    }

    public boolean containsData() {
        for (int file : fileIds()) {
            if (file(file) != null) return true;
        }
        return false;
    }
}
