package com.rspsi.osrs.rules.terrain;

import java.util.Objects;

/**
 * Formal OSRS terrain shadow contribution rules.
 *
 * <p>In OSRS, shadows are accumulated onto the shared terrain-corner grid rather
 * than per triangle, producing smooth multi-corner shadow gradients around structures.</p>
 */
public final class TerrainShadowRules {
    public static final int DEFAULT_WALL_SHADOW_STRENGTH = 50;

    private TerrainShadowRules() {}

    /**
     * Calculates the two shared corner offsets (dx0, dy0, dx1, dy1) shadowed by a straight wall (shape 0).
     */
    public static int[] straightWallCornerOffsets(int rotation) {
        return switch (rotation & 3) {
            case 0 -> new int[]{0, 0, 0, 1}; // West edge
            case 1 -> new int[]{0, 1, 1, 1}; // North edge
            case 2 -> new int[]{1, 0, 1, 1}; // East edge
            default -> new int[]{0, 0, 1, 0}; // South edge
        };
    }

    /**
     * Calculates the single shared corner offset (dx, dy) shadowed by a corner wall (shape 1 or 3).
     */
    public static int[] cornerWallOffset(int rotation) {
        return switch (rotation & 3) {
            case 0 -> new int[]{0, 1};
            case 1 -> new int[]{1, 1};
            case 2 -> new int[]{1, 0};
            default -> new int[]{0, 0};
        };
    }

    /**
     * Determines whether a location shape casts a terrain wall shadow.
     * Note: Shape 2 (L-wall) contributes occlusion flags but NO terrain shadow in vanilla OSRS.
     */
    public static boolean castsWallShadow(int shape) {
        return shape == 0 || shape == 1 || shape == 3;
    }
}
