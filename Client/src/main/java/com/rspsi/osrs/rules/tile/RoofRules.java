package com.rspsi.osrs.rules.tile;

/**
 * Formal OSRS rules for roof visibility, camera clipping, and interior occlusion.
 */
public final class RoofRules {
    private RoofRules() {}

    /**
     * Determines whether a location shape belongs to the OSRS roof category (shapes 12 through 21).
     */
    public static boolean isRoofShape(int shape) {
        return shape >= 12 && shape <= 21;
    }

    /**
     * Evaluates whether a roof instance should be rendered given the camera plane, tile flags, and roof toggle.
     */
    public static boolean shouldRenderRoof(
            int shape,
            int tileFlags,
            int cameraPlane,
            int tilePlane,
            boolean roofsEnabled
    ) {
        if (!roofsEnabled) return false;
        if (!isRoofShape(shape)) return true;

        // If inside a building (UNDER_ROOF), roofs at or above the player plane are removed
        if (TileFlagRules.isUnderRoof(tileFlags) && tilePlane >= cameraPlane) {
            return false;
        }

        return tilePlane >= cameraPlane;
    }
}
