package com.rspsi.cache;

/** Neutral capabilities exposed by a cache backend. */
public record CacheStoreCapabilities(
        boolean writable,
        boolean namedArchives,
        boolean mapPacking,
        CacheWriteMode writeMode
) {
    /** Compatibility constructor for existing neutral backends and fixtures. */
    public CacheStoreCapabilities(boolean writable, boolean namedArchives, boolean mapPacking) {
        this(writable, namedArchives, mapPacking,
                writable ? CacheWriteMode.DIRECT : CacheWriteMode.READ_ONLY);
    }

    public CacheStoreCapabilities {
        if (writeMode == null) {
            throw new NullPointerException("writeMode");
        }
        if (!writable && writeMode != CacheWriteMode.READ_ONLY) {
            throw new IllegalArgumentException("Non-writable capabilities must be READ_ONLY");
        }
        if (writable && writeMode == CacheWriteMode.READ_ONLY) {
            throw new IllegalArgumentException("Writable capabilities need a write mode");
        }
    }
}
