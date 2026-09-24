package com.rspsi.api;

/**
 * Axis-aligned bounding box of a model at one orientation, as
 * {@code net.runelite.api.AABB}: a centre and the half-extent along each axis,
 * in model-local units (Y negative-up).
 */
public interface AABB {
    int getCenterX();

    int getCenterY();

    int getCenterZ();

    int getExtremeX();

    int getExtremeY();

    int getExtremeZ();

    static AABB of(int centerX, int centerY, int centerZ, int extremeX, int extremeY, int extremeZ) {
        return new Box(centerX, centerY, centerZ, extremeX, extremeY, extremeZ);
    }

    record Box(int centerX, int centerY, int centerZ, int extremeX, int extremeY, int extremeZ) implements AABB {
        @Override public int getCenterX() { return centerX; }
        @Override public int getCenterY() { return centerY; }
        @Override public int getCenterZ() { return centerZ; }
        @Override public int getExtremeX() { return extremeX; }
        @Override public int getExtremeY() { return extremeY; }
        @Override public int getExtremeZ() { return extremeZ; }
    }
}
