package com.rspsi.cache;

/** Neutral capabilities exposed by a cache backend. */
public record CacheStoreCapabilities(
        boolean writable,
        boolean namedArchives,
        boolean mapPacking
) {
}
