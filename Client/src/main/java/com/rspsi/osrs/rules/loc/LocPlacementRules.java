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

    /** Applies a quarter-turn rotation to a stored location orientation. */
    public static int rotateOrientation(int rotation, int quarterTurns) {
        requireRotation(rotation);
        return (rotation + quarterTurns) & 3;
    }

    /**
     * Mirrors the location's placement orientation west/east.
     *
     * <p>This reflects OSRS placement semantics, not arbitrary model geometry:
     * the map format has quarter-turn rotation but no general mesh-mirror bit.
     * Straight/cardinal shapes and diagonal/corner shapes use different base
     * axes, matching the client's wall/decor/roof placement conventions.</p>
     */
    public static int mirrorOrientationX(int type, int rotation) {
        requireRotation(rotation);
        int offset = usesDiagonalMirrorAxis(type) ? 1 : 2;
        return (offset - rotation) & 3;
    }

    /** Mirrors the placement orientation north/south. */
    public static int mirrorOrientationY(int type, int rotation) {
        return rotateOrientation(mirrorOrientationX(type, rotation), 2);
    }

    /** Applies mirrors first, then quarter-turn rotation. */
    public static int transformOrientation(int type, int rotation,
                                           boolean mirrorX, boolean mirrorY,
                                           int quarterTurns) {
        requireRotation(rotation);
        int result = rotation;
        if (mirrorX) result = mirrorOrientationX(type, result);
        if (mirrorY) result = mirrorOrientationY(type, result);
        return rotateOrientation(result, quarterTurns);
    }

    /**
     * Shapes whose rotation-0 geometry is diagonal/corner-oriented rather than
     * cardinal. Reflection therefore maps its base orientation with offset 1
     * instead of offset 2.
     */
    private static boolean usesDiagonalMirrorAxis(int type) {
        return switch (type) {
            case 1, 2, 3, 6, 7, 8, 9, 11, 13, 14, 15, 16, 19, 20, 21 -> true;
            default -> false;
        };
    }

    private static void requireRotation(int rotation) {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Object rotation must be between 0 and 3");
        }
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
