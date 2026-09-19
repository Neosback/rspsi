package com.rspsi.osrs.rules.loc;

/**
 * Formal OSRS rules for location footprint rotation, anchor positioning, and center offsets.
 */
public final class LocPlacementRules {
    private LocPlacementRules() {}

    /** Returns the width of a location after rotation (0 or 2 preserve width; 1 or 3 swap). */
    public static int rotatedWidth(int width, int length, int rotation) {
        return (rotation & 1) == 0 ? width : length;
    }

    /** Returns the length of a location after rotation (0 or 2 preserve length; 1 or 3 swap). */
    public static int rotatedLength(int width, int length, int rotation) {
        return (rotation & 1) == 0 ? length : width;
    }

    /** Returns the center X offset in model-local units (half-tile per unit footprint). */
    public static int centerX(int footprintWidth) {
        return footprintWidth * 64;
    }

    /** Returns the center Z offset in model-local units (half-tile per unit footprint). */
    public static int centerZ(int footprintLength) {
        return footprintLength * 64;
    }
}
