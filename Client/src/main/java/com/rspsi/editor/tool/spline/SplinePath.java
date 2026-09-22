package com.rspsi.editor.tool.spline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure mathematical Catmull-Rom spline evaluator, normal ribbon generator,
 * and tile footprint rasterizer for spline-based map paths.
 */
public final class SplinePath {

    public static final class Point {
        public int x;
        public int y;
        public int plane;
        public int heightDelta;

        public Point(int x, int y, int plane) {
            this(x, y, plane, 0);
        }

        public Point(int x, int y, int plane, int heightDelta) {
            this.x = x;
            this.y = y;
            this.plane = plane;
            this.heightDelta = heightDelta;
        }

        public Point copy() {
            return new Point(x, y, plane, heightDelta);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Point other)) return false;
            return x == other.x && y == other.y && plane == other.plane && heightDelta == other.heightDelta;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, plane, heightDelta);
        }

        @Override
        public String toString() {
            return "Point{" + x + "," + y + " plane=" + plane + " h=" + heightDelta + "}";
        }
    }

    private final List<Point> points = new ArrayList<>();
    private int widthTiles = 2;

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public void clear() {
        points.clear();
    }

    public int widthTiles() {
        return widthTiles;
    }

    public void setWidthTiles(int widthTiles) {
        this.widthTiles = Math.max(1, Math.min(16, widthTiles));
    }

    public void addPoint(int x, int y, int plane) {
        points.add(new Point(x, y, plane));
    }

    public void addPoint(int x, int y, int plane, int heightDelta) {
        points.add(new Point(x, y, plane, heightDelta));
    }

    public void removePoint(int index) {
        if (index >= 0 && index < points.size()) {
            points.remove(index);
        }
    }

    public void movePoint(int index, int x, int y) {
        if (index >= 0 && index < points.size()) {
            Point p = points.get(index);
            p.x = x;
            p.y = y;
        }
    }

    public int findPointNear(int x, int y, int plane, int radiusTiles) {
        int bestIdx = -1;
        long bestDistSq = (long) radiusTiles * radiusTiles;

        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (p.plane == plane) {
                long dx = p.x - x;
                long dy = p.y - y;
                long distSq = dx * dx + dy * dy;
                if (distSq <= bestDistSq) {
                    bestDistSq = distSq;
                    bestIdx = i;
                }
            }
        }
        return bestIdx;
    }

    /**
     * Evaluates the Catmull-Rom spline with the given sampling step in tile units.
     * Returns an interleaved float array [x0, y0, x1, y1, ...].
     */
    public float[] evaluate(float step) {
        int count = points.size();
        if (count < 2) {
            return new float[0];
        }

        List<float[]> samples = new ArrayList<>();
        float minStep = Math.max(0.1f, step);

        for (int i = 0; i < count - 1; i++) {
            Point p0 = points.get(Math.max(0, i - 1));
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            Point p3 = points.get(Math.min(count - 1, i + 2));

            float segmentLength = (float) Math.hypot(p2.x - p1.x, p2.y - p1.y);
            int divisions = Math.max(2, (int) Math.ceil(segmentLength / minStep));

            for (int d = 0; d <= divisions; d++) {
                if (i > 0 && d == 0) continue; // avoid duplicate endpoints
                float t = (float) d / divisions;
                samples.add(catmullRom2D(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, t));
            }
        }

        float[] result = new float[samples.size() * 2];
        for (int i = 0; i < samples.size(); i++) {
            float[] s = samples.get(i);
            result[i * 2] = s[0];
            result[i * 2 + 1] = s[1];
        }
        return result;
    }

    /**
     * Rasterizes the spline curve into a continuous ribbon of tiles of the specified width.
     * Uses perpendicular curve normals to sweep across the width at each curve sample.
     */
    public Set<Long> rasterizeBand(float step, int width) {
        float[] samples = evaluate(step);
        if (samples.length == 0) {
            return Set.of();
        }

        int halfWidth = Math.max(0, width / 2);
        boolean even = (width % 2 == 0);
        Set<Long> tiles = new LinkedHashSet<>();
        int sampleCount = samples.length / 2;

        for (int i = 0; i < sampleCount; i++) {
            float cx = samples[i * 2];
            float cy = samples[i * 2 + 1];

            float tx;
            float ty;
            if (i == 0) {
                tx = samples[2] - samples[0];
                ty = samples[3] - samples[1];
            } else if (i == sampleCount - 1) {
                tx = samples[i * 2] - samples[(i - 1) * 2];
                ty = samples[i * 2 + 1] - samples[(i - 1) * 2 + 1];
            } else {
                tx = samples[(i + 1) * 2] - samples[(i - 1) * 2];
                ty = samples[(i + 1) * 2 + 1] - samples[(i - 1) * 2 + 1];
            }

            float len = (float) Math.hypot(tx, ty);
            if (len < 1e-4f) {
                tx = 1.0f;
                ty = 0.0f;
                len = 1.0f;
            }

            // Normal perpendicular to tangent
            float nx = -ty / len;
            float ny = tx / len;

            int minOffset = -halfWidth;
            int maxOffset = halfWidth - (even ? 1 : 0);

            for (int offset = minOffset; offset <= maxOffset; offset++) {
                int txCoord = Math.round(cx + nx * offset);
                int tyCoord = Math.round(cy + ny * offset);
                tiles.add(packCoord(txCoord, tyCoord));
            }
        }
        return Collections.unmodifiableSet(tiles);
    }

    public Set<Long> rasterize(float step) {
        return rasterizeBand(step, this.widthTiles);
    }

    /**
     * Computes the 4-bit neighbor mask for tile (x, y) relative to the rasterized path set.
     * Bit 0 (1): North (y + 1)
     * Bit 1 (2): East  (x + 1)
     * Bit 2 (4): South (y - 1)
     * Bit 3 (8): West  (x - 1)
     */
    public static int neighbourMask(Set<Long> pathTiles, int x, int y) {
        int mask = 0;
        if (pathTiles.contains(packCoord(x, y + 1))) mask |= 1;
        if (pathTiles.contains(packCoord(x + 1, y))) mask |= 2;
        if (pathTiles.contains(packCoord(x, y - 1))) mask |= 4;
        if (pathTiles.contains(packCoord(x - 1, y))) mask |= 8;
        return mask;
    }

    public static long packCoord(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackY(long packed) {
        return (int) packed;
    }

    private static float[] catmullRom2D(float x0, float y0, float x1, float y1,
                                       float x2, float y2, float x3, float y3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        float rx = 0.5f * (2.0f * x1 + (-x0 + x2) * t
                + (2.0f * x0 - 5.0f * x1 + 4.0f * x2 - x3) * t2
                + (-x0 + 3.0f * x1 - 3.0f * x2 + x3) * t3);

        float ry = 0.5f * (2.0f * y1 + (-y0 + y2) * t
                + (2.0f * y0 - 5.0f * y1 + 4.0f * y2 - y3) * t2
                + (-y0 + 3.0f * y1 - 3.0f * y2 + y3) * t3);

        return new float[]{rx, ry};
    }
}
