package com.rspsi.api.coords;

/**
 * Absolute world tile, as {@code net.runelite.api.coords.WorldPoint}.
 *
 * <p>World points are the stable identity of a location; scene and local
 * coordinates are projections relative to a loaded {@code WorldView}.</p>
 */
public record WorldPoint(int x, int y, int plane) {
    public WorldPoint {
        if (x < 0 || y < 0 || plane < 0 || plane > 3) {
            throw new IllegalArgumentException("Invalid world point " + x + "," + y + "," + plane);
        }
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getPlane() {
        return plane;
    }

    /** Region id in RuneLite/cache form: {@code (x >> 6) << 8 | (y >> 6)}. */
    public int getRegionID() {
        return ((x >> 6) << 8) | (y >> 6);
    }

    /** X offset inside the 64x64 region. */
    public int getRegionX() {
        return x & 63;
    }

    /** Y offset inside the 64x64 region. */
    public int getRegionY() {
        return y & 63;
    }

    public WorldPoint dx(int dx) {
        return new WorldPoint(x + dx, y, plane);
    }

    public WorldPoint dy(int dy) {
        return new WorldPoint(x, y + dy, plane);
    }

    public WorldPoint dz(int dz) {
        return new WorldPoint(x, y, plane + dz);
    }

    /** Chebyshev tile distance, or {@link Integer#MAX_VALUE} across planes. */
    public int distanceTo(WorldPoint other) {
        if (other.plane != plane) return Integer.MAX_VALUE;
        return Math.max(Math.abs(other.x - x), Math.abs(other.y - y));
    }

    public static WorldPoint fromRegion(int regionId, int regionX, int regionY, int plane) {
        return new WorldPoint(((regionId >>> 8) << 6) + regionX,
                ((regionId & 0xFF) << 6) + regionY, plane);
    }
}
