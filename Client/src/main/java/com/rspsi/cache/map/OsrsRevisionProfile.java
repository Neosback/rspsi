package com.rspsi.cache.map;

/**
 * Revision policy retained only for map-group layout discovery.
 *
 * <p>Terrain decoding is modern-only and belongs to Core. Revisions before the numeric packing
 * transition may still be inspected for named map-group discovery, but they do not imply support
 * for legacy terrain codecs.</p>
 */
public record OsrsRevisionProfile(int revision, MapGroupLayout mapGroupLayout) {
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
        return new OsrsRevisionProfile(revision, features.mapGroupLayout());
    }

    public enum MapGroupLayout {
        NAMED,
        NUMERIC
    }
}
