package com.rspsi.editor.model;

/** Neutral names for the terrain flags used by the OSRS map codec. */
public final class OsrsTileFlags {
    public static final int BLOCK_MAP_SQUARE = 0x1;
    public static final int BRIDGE = 0x2;
    public static final int REMOVE_ROOFS = 0x4;
    /** RuneLite Constants.TILE_FLAG_VIS_BELOW: tile may be visible from a lower scene level. */
    public static final int VIS_BELOW = 0x8;
    /** @deprecated Use {@link #VIS_BELOW}; retained for source compatibility. */
    @Deprecated
    public static final int MINIMAP_BRIDGE = VIS_BELOW;
    /** Tile is hidden from the normal minimap scene pass when either bit is set. */
    public static final int MINIMAP_HIDDEN = 0x18;

    private OsrsTileFlags() {
    }

    public static boolean hasBridge(int flags) {
        return (flags & BRIDGE) != 0;
    }

    public static boolean removesRoofs(int flags) {
        return (flags & REMOVE_ROOFS) != 0;
    }

    public static boolean visibleBelow(int flags) {
        return (flags & VIS_BELOW) != 0;
    }

    public static boolean isBlocked(int flags) {
        return (flags & BLOCK_MAP_SQUARE) != 0;
    }
}
