package com.rspsi.osrs.rules.tile;

/**
 * Tile render-flag bit definitions used by the legacy scene pipeline.
 *
 * <p>This enum is retained as a compatibility-facing representation while the canonical
 * first-party OSRS tile rules continue to live under the rspsi rules package.</p>
 */
public enum RenderFlags {
    BLOCKED_TILE(1),
    BRIDGE_TILE(2),
    FORCE_LOWEST_PLANE(4),
    RENDER_ON_LOWER_Z(8),
    DISABLE_RENDERING(16);

    private int bit;

    RenderFlags(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }
}
