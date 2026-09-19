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

    private TileShapeRules() {}

    /** Maps a tile overlay shape (0..11) to its 1..12 topology index, or 0 if no overlay. */
    public static int topologyIndex(int overlayId, int overlayShape) {
        if (overlayId <= 0) return 0;
        if (overlayShape < 0 || overlayShape >= OVERLAY_SHAPE_COUNT) {
            throw new IllegalArgumentException("Invalid overlay shape: " + overlayShape);
        }
        return overlayShape + 1;
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
