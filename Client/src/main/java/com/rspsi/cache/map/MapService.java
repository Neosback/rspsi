package com.rspsi.cache.map;

/** Neutral map access used by editors and scene builders. */
public interface MapService {
    MapIndexTable index();

    byte[] readLandscape(int regionX, int regionY);

    byte[] readObjects(int regionX, int regionY);
}
