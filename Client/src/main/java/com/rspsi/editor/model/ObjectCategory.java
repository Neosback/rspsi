package com.rspsi.editor.model;

/** Neutral semantic layer for an OSRS location shape. */
public enum ObjectCategory {
    WALL(0, "Wall"),
    WALL_DECOR(1, "Wall decor"),
    GROUND(2, "Game object"),
    GROUND_DECOR(3, "Ground decor"),
    UNKNOWN(-1, "Unknown");

    private final int layerId;
    private final String displayName;

    ObjectCategory(int layerId, String displayName) {
        this.layerId = layerId;
        this.displayName = displayName;
    }

    public int layerId() {
        return layerId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /** Maps the canonical OSRS location-shape range to its scene layer. */
    public static ObjectCategory fromType(int type) {
        return OsrsLocShape.fromId(type).map(OsrsLocShape::category).orElse(UNKNOWN);
    }
}
