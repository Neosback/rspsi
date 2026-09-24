package com.rspsi.editor.terrain.autotile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything within {@code halfWidth} tiles of a polyline: a path band with
 * round caps and round joins. Segments are bucketed per tile so a lookup only
 * tests the segments near the point.
 */
public final class StrokeRegion implements PathRegion {
    /** Points exactly on the band edge count as outside, so an edge on a tile border stays clean. */
    private static final double EDGE_EPSILON = 1e-6;

    private final List<double[]> points;
    private final double halfWidth;
    private final Map<Long, List<Integer>> buckets = new HashMap<>();
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;

    /** @param points polyline vertices {@code {x, y}} in tile units */
    public StrokeRegion(List<double[]> points, double halfWidth) {
        if (points.isEmpty()) throw new IllegalArgumentException("A stroke needs at least one point");
        if (!(halfWidth > 0.0)) throw new IllegalArgumentException("Half width must be positive");
        this.points = List.copyOf(points);
        this.halfWidth = halfWidth;
        double x0 = Double.MAX_VALUE;
        double y0 = Double.MAX_VALUE;
        double x1 = -Double.MAX_VALUE;
        double y1 = -Double.MAX_VALUE;
        for (double[] p : points) {
            x0 = Math.min(x0, p[0]);
            y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0]);
            y1 = Math.max(y1, p[1]);
        }
        minX = x0 - halfWidth;
        minY = y0 - halfWidth;
        maxX = x1 + halfWidth;
        maxY = y1 + halfWidth;
        int segments = Math.max(1, this.points.size() - 1);
        for (int s = 0; s < segments; s++) {
            double[] a = this.points.get(s);
            double[] b = this.points.get(Math.min(s + 1, this.points.size() - 1));
            int bx0 = (int) Math.floor(Math.min(a[0], b[0]) - halfWidth);
            int by0 = (int) Math.floor(Math.min(a[1], b[1]) - halfWidth);
            int bx1 = (int) Math.floor(Math.max(a[0], b[0]) + halfWidth);
            int by1 = (int) Math.floor(Math.max(a[1], b[1]) + halfWidth);
            for (int bx = bx0; bx <= bx1; bx++) {
                for (int by = by0; by <= by1; by++) {
                    buckets.computeIfAbsent(key(bx, by), k -> new ArrayList<>()).add(s);
                }
            }
        }
    }

    public double halfWidth() {
        return halfWidth;
    }

    public List<double[]> points() {
        return points;
    }

    /** Tiles whose square can touch the band: {@code {minTileX, minTileY, maxTileX, maxTileY}}. */
    public int[] tileBounds() {
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minY), (int) Math.floor(maxX), (int) Math.floor(maxY)};
    }

    @Override
    public boolean contains(double x, double y) {
        return distance(x, y) < halfWidth - EDGE_EPSILON;
    }

    /** Distance from the centre line; {@code +Infinity} far from every segment. */
    public double distance(double x, double y) {
        List<Integer> candidates = buckets.get(key((int) Math.floor(x), (int) Math.floor(y)));
        if (candidates == null) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (int s : candidates) {
            double[] a = points.get(s);
            double[] b = points.get(Math.min(s + 1, points.size() - 1));
            best = Math.min(best, segmentDistance(x, y, a[0], a[1], b[0], b[1]));
        }
        return best;
    }

    static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSq = dx * dx + dy * dy;
        double t = lengthSq == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx - px;
        double cy = ay + t * dy - py;
        return Math.sqrt(cx * cx + cy * cy);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
