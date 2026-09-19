package com.rspsi.osrs.rules.tile;

/**
 * Formal OSRS terrain tile flag rules and bitmasks.
 *
 * <p>Standardizes canonical flag semantics used across map decoders, collision builders,
 * and the scene renderer.</p>
 */
public final class TileFlagRules {
    /** Tile is impassable / blocked for movement. */
    public static final int BLOCKED = 0x1;

    /** Tile is part of a bridge structure (drawn/evaluated relative to plane below). */
    public static final int BRIDGE = 0x2;

    /** Tile is inside or under a roof structure. */
    public static final int UNDER_ROOF = 0x4;

    /** Upper-plane bridge/roof visibility bit used by the scene minimap. */
    public static final int VIS_BELOW = 0x8;

    /** Tile is hidden from the normal minimap pass when either 0x8 or 0x10 is set. */
    public static final int MINIMAP_HIDDEN = 0x18;

    public record ResolvedTileFlags(
            boolean blocked,
            boolean bridge,
            boolean underRoof,
            boolean visibleBelow,
            boolean hiddenFromMinimap
    ) {}

    private TileFlagRules() {}

    public static boolean isBlocked(int flags) {
        return (flags & BLOCKED) != 0;
    }

    public static boolean isBridge(int flags) {
        return (flags & BRIDGE) != 0;
    }

    public static boolean isUnderRoof(int flags) {
        return (flags & UNDER_ROOF) != 0;
    }

    public static boolean isVisibleBelow(int flags) {
        return (flags & VIS_BELOW) != 0;
    }

    public static boolean hiddenFromMinimap(int flags) {
        return (flags & MINIMAP_HIDDEN) != 0;
    }

    public static ResolvedTileFlags resolve(int flags) {
        return new ResolvedTileFlags(
                isBlocked(flags),
                isBridge(flags),
                isUnderRoof(flags),
                isVisibleBelow(flags),
                hiddenFromMinimap(flags)
        );
    }
}
