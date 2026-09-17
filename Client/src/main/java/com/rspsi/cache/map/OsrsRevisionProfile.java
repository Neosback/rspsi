package com.rspsi.cache.map;

/**
 * Small, explicit revision policy for cache-boundary decisions.
 *
 * <p>This is deliberately not part of the world model. Revision-specific
 * layout decisions stay at the cache/map boundary where they can be audited
 * and replaced without leaking archive conventions into editor code.</p>
 */
public record OsrsRevisionProfile(int revision, MapGroupLayout mapGroupLayout,
                                  boolean newTerrainFormat) {

    /**
     * Compatibility constructor for callers that have not selected a
     * revision profile yet. Synthetic fixtures historically used the modern
     * two-byte terrain representation, so keep that behavior explicit here.
     */
    public OsrsRevisionProfile(int revision, MapGroupLayout mapGroupLayout) {
        this(revision, mapGroupLayout, true);
    }

    public OsrsRevisionProfile {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }
        if (mapGroupLayout == null) {
            throw new NullPointerException("mapGroupLayout");
        }
    }

    /** OpenRune's map packer uses numeric groups from revision 237 onward. */
    public static OsrsRevisionProfile forRevision(int revision) {
        OsrsRevisionFeatures features = OsrsRevisionFeatures.forRevision(revision);
        return new OsrsRevisionProfile(revision, features.mapGroupLayout(),
                features.usesShortTerrainValues());
    }

    public enum MapGroupLayout {
        NAMED,
        NUMERIC
    }
}
