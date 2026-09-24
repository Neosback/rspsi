package com.rspsi.editor.terrain.autotile;

import com.rspsi.osrs.rules.tile.TileShapeRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each OSRS overlay shape puts its overlay inside a tile, derived from the
 * client's own tile triangulation ({@code runescape-client/SceneTileModel}:
 * {@code triangleTextureIndices}, {@code faceIndices} and the rotation rules in
 * its constructor, mirrored in {@link TileShapeRules}).
 *
 * <p>Tile space is {@code [0,1] x [0,1]}, x east and y north. For every map
 * overlay shape (0-11) and rotation (0-3) the atlas holds the overlay
 * triangles, an 8x8 coverage bitmask and an 8-bit edge ("portal") signature:
 * which of the eight half-edges of the tile border the overlay touches. Two
 * tiles join seamlessly when their shared half-edges agree, which is what the
 * {@link OverlayAutotiler} enforces.</p>
 */
public final class OverlayShapeAtlas {
    /** Coverage bitmask resolution per axis; 8 x 8 samples fit a {@code long}. */
    public static final int RESOLUTION = 8;
    public static final int SHAPES = TileShapeRules.OVERLAY_SHAPE_COUNT;
    public static final long FULL_COVERAGE = -1L;
    public static final int ALL_PORTALS = 0xFF;

    /**
     * Half-edge midpoints, counter-clockwise from the south edge's west half:
     * S-west, S-east, E-south, E-north, N-east, N-west, W-north, W-south.
     */
    public static final double[][] PORTAL_POINTS = {
            {0.25, 0.0}, {0.75, 0.0}, {1.0, 0.25}, {1.0, 0.75},
            {0.75, 1.0}, {0.25, 1.0}, {0.0, 0.75}, {0.0, 0.25}};

    /** Client tile points 1-16 in 1/128 tile units ({@code SceneTileModel} constructor). */
    private static final int[][] POINTS = {
            null,
            {0, 0}, {64, 0}, {128, 0}, {128, 64}, {128, 128}, {64, 128}, {0, 128}, {0, 64},
            {64, 32}, {96, 64}, {64, 96}, {32, 64},
            {32, 32}, {96, 32}, {96, 96}, {32, 96}};

    private static final double NUDGE = 0.03;
    private static final Entry[][] ENTRIES = new Entry[SHAPES][4];

    static {
        for (int shape = 0; shape < SHAPES; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                ENTRIES[shape][rotation] = build(shape, rotation);
            }
        }
    }

    /** One shape at one rotation. Triangles are {@code {ax, ay, bx, by, cx, cy}} in tile space. */
    public record Entry(int shape, int rotation, List<double[]> overlayTriangles, long coverage, int portals) {
        public Entry {
            overlayTriangles = List.copyOf(overlayTriangles);
        }

        public boolean covers(double x, double y) {
            for (double[] t : overlayTriangles) {
                if (inTriangle(x, y, t)) return true;
            }
            return false;
        }

        /** Fraction of the tile covered by overlay, from the coverage mask. */
        public float coverageFraction() {
            return Long.bitCount(coverage) / (float) (RESOLUTION * RESOLUTION);
        }
    }

    private OverlayShapeAtlas() {
    }

    public static Entry entry(int shape, int rotation) {
        if (shape < 0 || shape >= SHAPES) throw new IllegalArgumentException("Overlay shape must be 0-11: " + shape);
        return ENTRIES[shape][rotation & 3];
    }

    /** Sample index for the coverage mask at column {@code i}, row {@code j} (row 0 = south). */
    public static int bit(int i, int j) {
        return j * RESOLUTION + i;
    }

    /**
     * Coverage sample {@code i} along x, in tile space. Samples sit slightly off
     * the cell centres so none lands exactly on a shape edge (the client's edges
     * run through quarter points and the diagonals between them), where the
     * in/out answer would be arbitrary.
     */
    public static double sampleX(int i) {
        return (i + 0.5) / RESOLUTION + 0.013;
    }

    /** Coverage sample {@code j} along y; see {@link #sampleX}. */
    public static double sampleY(int j) {
        return (j + 0.5) / RESOLUTION + 0.007;
    }

    /**
     * Where portal {@code p} is sampled: the half-edge midpoint moved just inside
     * the tile, so it reads the one triangle on that half-edge and an area whose
     * border runs exactly along the tile edge counts as touching it only from
     * the inside.
     */
    public static double[] portalSample(int p) {
        double x = PORTAL_POINTS[p][0];
        double y = PORTAL_POINTS[p][1];
        x += x == 0.0 ? NUDGE : x == 1.0 ? -NUDGE : 0.0;
        y += y == 0.0 ? NUDGE : y == 1.0 ? -NUDGE : 0.0;
        return new double[]{x, y};
    }

    private static Entry build(int mapShape, int rotation) {
        int modelShape = mapShape + 1;
        int[] vertices = TileShapeRules.SHAPE_POINTS[modelShape];
        double[][] positions = new double[vertices.length][];
        for (int i = 0; i < vertices.length; i++) {
            int point = rotatePoint(vertices[i], rotation);
            positions[i] = new double[]{POINTS[point][0] / 128.0, POINTS[point][1] / 128.0};
        }
        int[] faces = TileShapeRules.ELEMENTS[modelShape];
        List<double[]> overlay = new ArrayList<>();
        for (int f = 0; f + 3 < faces.length; f += 4) {
            if (faces[f] != 1) continue;
            double[] a = positions[faceVertex(faces[f + 1], rotation)];
            double[] b = positions[faceVertex(faces[f + 2], rotation)];
            double[] c = positions[faceVertex(faces[f + 3], rotation)];
            overlay.add(new double[]{a[0], a[1], b[0], b[1], c[0], c[1]});
        }
        Entry probe = new Entry(mapShape, rotation, overlay, 0L, 0);
        long coverage = 0L;
        for (int j = 0; j < RESOLUTION; j++) {
            for (int i = 0; i < RESOLUTION; i++) {
                if (probe.covers(sampleX(i), sampleY(j))) coverage |= 1L << bit(i, j);
            }
        }
        int portals = 0;
        for (int p = 0; p < 8; p++) {
            double[] point = portalSample(p);
            if (probe.covers(point[0], point[1])) portals |= 1 << p;
        }
        return new Entry(mapShape, rotation, overlay, coverage, portals);
    }

    /** {@code SceneTileModel}: mid-edge and inner points rotate with the tile; corners rotate by face index. */
    private static int rotatePoint(int point, int rotation) {
        if ((point & 1) == 0 && point <= 8) return ((point - rotation - rotation - 1) & 7) + 1;
        if (point > 8 && point <= 12) return ((point - 9 - rotation) & 3) + 9;
        if (point > 12 && point <= 16) return ((point - 13 - rotation) & 3) + 13;
        return point;
    }

    private static int faceVertex(int index, int rotation) {
        return index < 4 ? (index - rotation) & 3 : index;
    }

    static boolean inTriangle(double x, double y, double[] t) {
        double d1 = cross(x, y, t[0], t[1], t[2], t[3]);
        double d2 = cross(x, y, t[2], t[3], t[4], t[5]);
        double d3 = cross(x, y, t[4], t[5], t[0], t[1]);
        boolean negative = d1 < -1e-9 || d2 < -1e-9 || d3 < -1e-9;
        boolean positive = d1 > 1e-9 || d2 > 1e-9 || d3 > 1e-9;
        return !(negative && positive);
    }

    private static double cross(double px, double py, double ax, double ay, double bx, double by) {
        return (px - bx) * (ay - by) - (ax - bx) * (py - by);
    }
}
