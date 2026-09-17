package com.rspsi.cache;

/** Describes where a cache backend sends accepted writes. */
public enum CacheWriteMode {
    /** The backend accepts no writes. */
    READ_ONLY,
    /** Writes are committed directly to the backend. */
    DIRECT,
    /** Writes are held until an explicit flush commits them to an output layer. */
    STAGED,
    /** The backend can build or pack an output but is not an editor write target. */
    BUILD_ONLY
}
