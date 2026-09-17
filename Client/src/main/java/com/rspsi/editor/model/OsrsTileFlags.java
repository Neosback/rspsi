package com.rspsi.editor.model;

/** Neutral names for the terrain flags used by the OSRS map codec. */
public final class OsrsTileFlags {
    public static final int BLOCK_MAP_SQUARE = 0x1;
    public static final int BRIDGE = 0x2;
    public static final int REMOVE_ROOFS = 0x4;
    /** Upper-plane bridge/roof visibility bit used by the scene minimap. */
    public static final int MINIMAP_BRIDGE = 0x8;
    /** Tile is hidden from the normal minimap scene pass when either bit is set. */
    public static final int MINIMAP_HIDDEN = 0x18;

    private OsrsTileFlags() {
    }

    public static boolean hasBridge(int flags) {
        return (flags & BRIDGE) != 0;
    }
}
