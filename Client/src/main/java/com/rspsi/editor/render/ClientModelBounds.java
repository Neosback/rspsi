package com.rspsi.editor.render;

import java.util.List;
import java.util.Objects;

/**
 * RuneLite/client model bounds calculated in model-local coordinates.
 *
 * <p>This is deliberately separate from both packet geometry min/max and
 * {@link GameObjectSceneMetadata}. The client uses cylinder bounds for draw
 * visibility/depth ordering and an orientation-specific AABB for clickbox and
 * bounding-box tests before scene translation is applied.</p>
 */
public record ClientModelBounds(
        boolean present,
        int height,
        int bottomY,
        int xzRadius,
        int radius,
        int diameter,
        boolean singleTile,
        Aabb drawAabb
) {
    private static final double ANGLE_STEP = 0.0030679615D;
    private static final int[] SINE = new int[2048];
    private static final int[] COSINE = new int[2048];
    private static final ClientModelBounds NONE =
            new ClientModelBounds(false, 0, 0, 0, 0, 0, false, Aabb.none());

    static {
        for (int orientation = 0; orientation < 2048; orientation++) {
            SINE[orientation] = (int) (65536.0D * Math.sin(orientation * ANGLE_STEP));
            COSINE[orientation] = (int) (65536.0D * Math.cos(orientation * ANGLE_STEP));
        }
    }

    public ClientModelBounds {
        drawAabb = Objects.requireNonNull(drawAabb, "drawAabb");
        if (present && (height < 0 || bottomY < 0 || xzRadius < 0
                || radius < 0 || diameter < 0 || !drawAabb.present())) {
            throw new IllegalArgumentException("Invalid client model bounds");
        }
    }

    public static ClientModelBounds none() {
        return NONE;
    }

    /**
     * Mirrors client Model.calculateBoundsCylinder + calculateBoundingBox.
     * Vertices must be model-local and already contain model transforms/contouring,
     * but must not contain scene-center or wall-decoration placement translation.
     */
    public static ClientModelBounds calculate(List<ModelVertex> vertices,
                                              int drawOrientation,
                                              boolean singleTile) {
        Objects.requireNonNull(vertices, "vertices");
        if (vertices.isEmpty()) return none();
        int orientation = drawOrientation & 2047;

        int height = 0;
        int bottomY = 0;
        int xzRadiusSquared = 0;
        for (ModelVertex vertex : vertices) {
            if (-vertex.y() > height) height = -vertex.y();
            if (vertex.y() > bottomY) bottomY = vertex.y();
            int radial = vertex.x() * vertex.x() + vertex.z() * vertex.z();
            if (radial > xzRadiusSquared) xzRadiusSquared = radial;
        }

        int xzRadius = ceilClientSqrt(xzRadiusSquared);
        int radius = ceilClientSqrt(xzRadius * xzRadius + height * height);
        int diameter = radius
                + ceilClientSqrt(xzRadius * xzRadius + bottomY * bottomY);

        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        int cosine = COSINE[orientation];
        int sine = SINE[orientation];
        for (ModelVertex vertex : vertices) {
            int rotatedX = vertex.x() * cosine + sine * vertex.z() >> 16;
            int rotatedZ = vertex.z() * cosine - sine * vertex.x() >> 16;
            int y = vertex.y();
            if (rotatedX < minX) minX = rotatedX;
            if (rotatedX > maxX) maxX = rotatedX;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (rotatedZ < minZ) minZ = rotatedZ;
            if (rotatedZ > maxZ) maxZ = rotatedZ;
        }

        int xMid = (maxX + minX) / 2;
        int yMid = (maxY + minY) / 2;
        int zMid = (maxZ + minZ) / 2;
        int xMidOffset = (maxX - minX + 1) / 2;
        int yMidOffset = (maxY - minY + 1) / 2;
        int zMidOffset = (maxZ - minZ + 1) / 2;
        if (xMidOffset < 32) xMidOffset = 32;
        if (zMidOffset < 32) zMidOffset = 32;
        if (singleTile) {
            xMidOffset += 8;
            zMidOffset += 8;
        }

        return new ClientModelBounds(true, height, bottomY, xzRadius, radius, diameter,
                singleTile, new Aabb(true, orientation, xMid, yMid, zMid,
                        xMidOffset, yMidOffset, zMidOffset));
    }

    private static int ceilClientSqrt(int value) {
        return (int) (Math.sqrt((double) value) + 0.99D);
    }

    /** Orientation-specific client AABB midpoint + half-extents. */
    public record Aabb(
            boolean present,
            int orientation,
            int xMid,
            int yMid,
            int zMid,
            int xMidOffset,
            int yMidOffset,
            int zMidOffset
    ) {
        private static final Aabb NONE = new Aabb(false, 0, 0, 0, 0, 0, 0, 0);

        public Aabb {
            if (present && (orientation < 0 || orientation >= 2048
                    || xMidOffset < 0 || yMidOffset < 0 || zMidOffset < 0)) {
                throw new IllegalArgumentException("Invalid client model AABB");
            }
        }

        public static Aabb none() {
            return NONE;
        }

        public int minX() { return xMid - xMidOffset; }
        public int maxX() { return xMid + xMidOffset; }
        public int minY() { return yMid - yMidOffset; }
        public int maxY() { return yMid + yMidOffset; }
        public int minZ() { return zMid - zMidOffset; }
        public int maxZ() { return zMid + zMidOffset; }
    }
}
