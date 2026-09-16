package com.rspsi.cache.map;

/**
 * Small, explicit revision policy for cache-boundary decisions.
 *
 * <p>This is deliberately not part of the world model. Revision-specific
 * layout decisions stay at the cache/map boundary where they can be audited
 * and replaced without leaking archive conventions into editor code.</p>
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
        return new OsrsRevisionProfile(revision,
                revision >= 237 ? MapGroupLayout.NUMERIC : MapGroupLayout.NAMED);
    }

    public enum MapGroupLayout {
        NAMED,
        NUMERIC
    }
}
