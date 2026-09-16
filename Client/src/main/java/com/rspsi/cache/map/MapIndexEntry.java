package com.rspsi.cache.map;

/**
 * Cache-neutral description of the two files belonging to a map region.
 * Archive IDs are optional because named OSRS archives are the canonical
 * lookup, while older adapters may only be able to provide numeric IDs.
 */
public record MapIndexEntry(
        int regionX,
        int regionY,
        int landscapeArchiveId,
        int objectArchiveId,
        String landscapeName,
        String objectName
) {
    public MapIndexEntry {
        if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
            throw new IllegalArgumentException("OSRS region coordinates must be in [0, 255]");
        }
        if (landscapeArchiveId < -1 || objectArchiveId < -1) {
            throw new IllegalArgumentException("Archive IDs must be -1 or non-negative");
        }
        if (landscapeName == null || landscapeName.isBlank()
                || objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Map archive names are required");
        }
    }

    public int key() {
        return (regionX << 8) | regionY;
    }

    public boolean hasLandscape() {
        return landscapeArchiveId >= 0;
    }

    public boolean hasObjects() {
        return objectArchiveId >= 0;
    }
}
