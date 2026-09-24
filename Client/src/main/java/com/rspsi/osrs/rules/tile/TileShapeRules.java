package com.rspsi.osrs.rules.tile;

import java.util.Objects;

/**
 * Formal OSRS overlay and underlay tile shape rules and geometry tables.
 *
 * <p>The OSRS map format encodes overlay shapes as indices 0..11.
 * In the client's scene topology table, shape 0 represents the full flat tile,
 * while overlay shapes occupy topology entries 1..12.</p>
 */
public final class TileShapeRules {
    public static final int PLAIN_TILE = 0;
    public static final int OVERLAY_SHAPE_COUNT = 12;

    public static final int[][] SHAPE_POINTS = {
            {1, 3, 5, 7},
            {1, 3, 5, 7},
            {1, 3, 5, 7},
            {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 6},
            {1, 3, 5, 7, 2, 6},
            {1, 3, 5, 7, 2, 8},
            {1, 3, 5, 7, 2, 8},
            {1, 3, 5, 7, 11, 12},
            {1, 3, 5, 7, 11, 12},
            {1, 3, 5, 7, 13, 14}
    };

    public static final int[][] ELEMENTS = {
            {0, 1, 2, 3, 0, 0, 1, 3},
            {1, 1, 2, 3, 1, 0, 1, 3},
            {0, 1, 2, 3, 1, 0, 1, 3},
            {0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3},
            {0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4},
            {0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4},
            {0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3},
            {0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3},
            {0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5},
            {0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5},
            {0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3},
            {1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3},
            {1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5}
    };

    private static final int[] MIRROR_X_SHAPE = {
            0, 1, 3, 2, 5, 4, 6, 7, 8, 9, 10, 11
    };

    /**
     * For a west/east mirror, the equivalent OSRS rotation is
     * {@code (offset[shape] - rotation) & 3}. These values are derived from
     * the same SceneTileModel topology represented by {@link #SHAPE_POINTS}
     * and {@link #ELEMENTS}. Shape 0 is a full overlay and is canonicalized to
     * rotation 0.
     */
    private static final int[] MIRROR_X_ROTATION_OFFSET = {
            0, 3, 0, 0, 0, 0, 2, 3, 3, 1, 1, 0
    };

    private TileShapeRules() {}

    public record OverlayTransform(int shape, int rotation) {
        public OverlayTransform {
            if (shape < 0 || shape >= OVERLAY_SHAPE_COUNT) {
                throw new IllegalArgumentException("Invalid overlay shape: " + shape);
            }
            if (rotation < 0 || rotation > 3) {
                throw new IllegalArgumentException("Invalid overlay rotation: " + rotation);
            }
        }
    }

    /** Maps a tile overlay shape (0..11) to its 1..12 topology index, or 0 if no overlay. */
    public static int topologyIndex(int overlayId, int overlayShape) {
        if (overlayId <= 0) return 0;
        if (overlayShape < 0 || overlayShape >= OVERLAY_SHAPE_COUNT) {
            throw new IllegalArgumentException("Invalid overlay shape: " + overlayShape);
        }
        return overlayShape + 1;
    }

    /** Rotates a stored overlay by quarter turns in world tile space. */
    public static OverlayTransform rotateOverlay(int shape, int rotation, int quarterTurns) {
        requireOverlay(shape, rotation);
        int turns = quarterTurns & 3;
        if (shape == 0) return new OverlayTransform(0, 0);
        return new OverlayTransform(shape, (rotation + turns) & 3);
    }

    /**
     * Mirrors a stored overlay west/east in tile space.
     *
     * <p>Several native OSRS shapes change shape ID under reflection rather
     * than merely changing rotation. The mapping is the exact equivalent
     * topology representable by SceneTileModel.</p>
     */
    public static OverlayTransform mirrorOverlayX(int shape, int rotation) {
        requireOverlay(shape, rotation);
        if (shape == 0) return new OverlayTransform(0, 0);
        return new OverlayTransform(
                MIRROR_X_SHAPE[shape],
                (MIRROR_X_ROTATION_OFFSET[shape] - rotation) & 3);
    }

    /**
     * Mirrors a stored overlay north/south in tile space.
     *
     * <p>North/south reflection is the west/east reflection followed by a
     * 180-degree rotation, which keeps the two mirror implementations tied to
     * one verified topology table.</p>
     */
    public static OverlayTransform mirrorOverlayY(int shape, int rotation) {
        OverlayTransform mirrored = mirrorOverlayX(shape, rotation);
        return rotateOverlay(mirrored.shape(), mirrored.rotation(), 2);
    }

    /**
     * Applies mirrors first, then quarter-turn rotation, matching fragment
     * spatial-transform composition.
     */
    public static OverlayTransform transformOverlay(int shape, int rotation,
                                                    boolean mirrorX, boolean mirrorY,
                                                    int quarterTurns) {
        OverlayTransform value = new OverlayTransform(shape, rotation);
        if (mirrorX) value = mirrorOverlayX(value.shape(), value.rotation());
        if (mirrorY) value = mirrorOverlayY(value.shape(), value.rotation());
        return rotateOverlay(value.shape(), value.rotation(), quarterTurns);
    }

    private static void requireOverlay(int shape, int rotation) {
        if (shape < 0 || shape >= OVERLAY_SHAPE_COUNT) {
            throw new IllegalArgumentException("Invalid overlay shape: " + shape);
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Invalid overlay rotation: " + rotation);
        }
    }

    /** Rotates an OSRS tile mesh point index (1..14) by quarter turns (0..3). */
    public static int rotatePoint(int point, int rotation) {
        if (point <= 0) return point;
        return switch (point) {
            case 1, 3, 5, 7 -> 1 + ((point - 1 - rotation * 2) & 7);
            case 2, 4, 6, 8 -> 1 + ((point - 1 - rotation * 2) & 7);
            case 9, 10 -> 9 + ((point - 9 - rotation) & 1);
            case 11, 12, 13, 14 -> 11 + ((point - 11 - rotation) & 3);
            default -> point;
        };
    }
}
