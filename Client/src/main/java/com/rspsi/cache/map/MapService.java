package com.rspsi.cache.map;

/** Neutral map access used by editors and scene builders. */
public interface MapService {
    MapIndexTable index();

    byte[] readLandscape(int regionX, int regionY);

    /** Reads the OSRS location (loc/object placement) payload. */
    byte[] readLocations(int regionX, int regionY);

    /** Temporary source-compatible name retained for existing loaders. */
    @Deprecated
    default byte[] readObjects(int regionX, int regionY) {
        return readLocations(regionX, regionY);
    }
}
