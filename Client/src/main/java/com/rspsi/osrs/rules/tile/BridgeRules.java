package com.rspsi.osrs.rules.tile;

/**
 * Formal OSRS rules for bridge elevation and cross-plane layer resolution.
 */
public final class BridgeRules {
    private BridgeRules() {}

    /**
     * Resolves the effective rendering and collision plane for a tile.
     * In OSRS, a bridge on plane 1 renders and collides on plane 0.
     */
    public static int resolveEffectivePlane(int plane, int tileFlags) {
        if (plane > 0 && TileFlagRules.isBridge(tileFlags)) {
            return plane - 1;
        }
        return plane;
    }

    /**
     * Determines whether a tile on an upper plane should be considered an active bridge link.
     */
    public static boolean isBridge(int tileFlags) {
        return TileFlagRules.isBridge(tileFlags);
    }
}
