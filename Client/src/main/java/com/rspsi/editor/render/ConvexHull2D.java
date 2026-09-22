package com.rspsi.editor.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Jarvis march (gift wrapping) convex hull over a 2D point set.
 *
 * <p>This is the same technique RuneLite's {@code Model.getConvexHull()} uses
 * to highlight a game object: project every model vertex to screen space,
 * then wrap a hull around the resulting point cloud so the outline hugs the
 * object's actual silhouette instead of a generic bounding box.</p>
 */
public final class ConvexHull2D {
    private ConvexHull2D() {
    }

    /** Each point is {@code {x, y}}. Returns the hull in winding order, or the input unchanged if fewer than 3 points. */
    public static List<float[]> compute(List<float[]> points) {
        int n = points.size();
        if (n < 3) return List.copyOf(points);

        int leftmost = 0;
        for (int i = 1; i < n; i++) {
            if (points.get(i)[0] < points.get(leftmost)[0]) leftmost = i;
        }

        List<float[]> hull = new ArrayList<>();
        int current = leftmost;
        do {
            hull.add(points.get(current));
            int next = (current + 1) % n;
            for (int i = 0; i < n; i++) {
                if (orientation(points.get(current), points.get(next), points.get(i)) < 0.0f) {
                    next = i;
                }
            }
            current = next;
            // Degenerate input (duplicate/collinear points) could otherwise loop forever.
            if (hull.size() > n) break;
        } while (current != leftmost);
        return hull;
    }

    /** Cross product sign of (b-a) x (c-a): negative means c is clockwise of a->b in screen space. */
    private static float orientation(float[] a, float[] b, float[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }
}
