package com.rspsi.cache.store;

import java.util.Objects;

/** Neutral view of one cache index for compatibility loaders. */
public final class CacheIndexView {
    private final CacheStore store;
    private final int index;

    public CacheIndexView(CacheStore store, int index) {
        this.store = Objects.requireNonNull(store, "store");
        if (index < 0) throw new IllegalArgumentException("Cache index cannot be negative");
        this.index = index;
    }

    public int id() {
        return index;
    }

    public int[] archiveIds() {
        return store.archiveIds(index).clone();
    }

    public CacheArchiveView archive(int archive) {
        return new CacheArchiveView(store, index, archive);
    }

    public CacheArchiveView[] archives() {
        return java.util.Arrays.stream(archiveIds())
                .mapToObj(this::archive)
                .toArray(CacheArchiveView[]::new);
    }
}
