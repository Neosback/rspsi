package com.rspsi.editor.model;

/** Neutral names for the terrain flags used by the OSRS map codec. */
public final class OsrsTileFlags {
    public static final int BLOCK_MAP_SQUARE = 0x1;
    public static final int BRIDGE = 0x2;
    public static final int REMOVE_ROOFS = 0x4;

    private OsrsTileFlags() {
    }

    public static boolean hasBridge(int flags) {
        return (flags & BRIDGE) != 0;
    }
}
