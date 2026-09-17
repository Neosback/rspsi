package com.rspsi.cache.map;

/**
 * Revision-derived cache features used by the neutral OSRS boundary.
 *
 * <p>This is the single policy object for format decisions that are known to
 * vary across the currently supported OSRS map revisions. It deliberately
 * contains no archive, cache-library, or editor-world types.</p>
 */
public record OsrsRevisionFeatures(
        int revision,
        OsrsRevisionProfile.MapGroupLayout mapGroupLayout,
        TerrainValueFormat terrainValueFormat) {

    public OsrsRevisionFeatures {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }
        if (mapGroupLayout == null) {
            throw new NullPointerException("mapGroupLayout");
        }
        if (terrainValueFormat == null) {
            throw new NullPointerException("terrainValueFormat");
        }
    }

    /** Returns the audited policy for a revision supported by the map codec. */
    public static OsrsRevisionFeatures forRevision(int revision) {
        return new OsrsRevisionFeatures(
                revision,
                revision >= 237
                        ? OsrsRevisionProfile.MapGroupLayout.NUMERIC
                        : OsrsRevisionProfile.MapGroupLayout.NAMED,
                revision >= 209 ? TerrainValueFormat.SHORT : TerrainValueFormat.BYTE);
    }

    public boolean usesNumericMapGroups() {
        return mapGroupLayout == OsrsRevisionProfile.MapGroupLayout.NUMERIC;
    }

    public boolean usesShortTerrainValues() {
        return terrainValueFormat == TerrainValueFormat.SHORT;
    }

    public enum TerrainValueFormat {
        BYTE,
        SHORT
    }
}
