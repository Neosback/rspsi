package com.rspsi.cache.map;

/**
 * Revision-derived map-index features used by the neutral OSRS boundary.
 *
 * <p>Terrain-width compatibility was retired when modern region codecs moved to Core. The only
 * remaining revision-dependent map concern here is named versus numeric archive grouping.</p>
 */
public record OsrsRevisionFeatures(
        int revision,
        OsrsRevisionProfile.MapGroupLayout mapGroupLayout) {

    public OsrsRevisionFeatures {
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }
        if (mapGroupLayout == null) {
            throw new NullPointerException("mapGroupLayout");
        }
    }

    public static OsrsRevisionFeatures forRevision(int revision) {
        return new OsrsRevisionFeatures(
                revision,
                revision >= 237
                        ? OsrsRevisionProfile.MapGroupLayout.NUMERIC
                        : OsrsRevisionProfile.MapGroupLayout.NAMED);
    }

    public boolean usesNumericMapGroups() {
        return mapGroupLayout == OsrsRevisionProfile.MapGroupLayout.NUMERIC;
    }
}
