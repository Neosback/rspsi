package com.rspsi.editor.knowledge.derived;

import java.util.Objects;

/**
 * Layer 3 Derived Fact: Geometric topology characteristics of a single terrain tile.
 */
public record TerrainTopology(
        int minHeight,
        int maxHeight,
        int heightVariance,
        double slopeMagnitude,
        Aspect aspect,
        Curvature curvature,
        boolean isCliff
) {
    public enum Aspect {
        FLAT, NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST
    }

    public enum Curvature {
        FLAT, CONVEX, CONCAVE, SADDLE
    }

    public TerrainTopology {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(curvature, "curvature");
    }

    public static TerrainTopology calculate(int sw, int se, int ne, int nw) {
        int min = Math.min(Math.min(sw, se), Math.min(ne, nw));
        int max = Math.max(Math.max(sw, se), Math.max(ne, nw));
        int variance = max - min;
        boolean cliff = variance >= 120;

        // Gradient vector across tile
        // X gradient: east corners - west corners
        double dx = ((se + ne) - (sw + nw)) / 2.0;
        // Y gradient: north corners - south corners
        double dy = ((ne + nw) - (sw + se)) / 2.0;
        double slopeMag = Math.sqrt(dx * dx + dy * dy);

        Aspect asp;
        if (slopeMag < 4.0) {
            asp = Aspect.FLAT;
        } else {
            double angle = Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
            if (angle < 0) angle += 360.0;
            // Angle 0 is East (+X), 90 is North (+Y), 180 is West (-X), 270 is South (-Y)
            if (angle >= 337.5 || angle < 22.5) asp = Aspect.EAST;
            else if (angle < 67.5) asp = Aspect.NORTHEAST;
            else if (angle < 112.5) asp = Aspect.NORTH;
            else if (angle < 157.5) asp = Aspect.NORTHWEST;
            else if (angle < 202.5) asp = Aspect.WEST;
            else if (angle < 247.5) asp = Aspect.SOUTHWEST;
            else if (angle < 292.5) asp = Aspect.SOUTH;
            else asp = Aspect.SOUTHEAST;
        }

        // Curvature calculation: compare center height with corners
        double avgCorner = (sw + se + ne + nw) / 4.0;
        double diag1Diff = (ne + sw) / 2.0 - avgCorner;
        double diag2Diff = (nw + se) / 2.0 - avgCorner;

        Curvature curv;
        if (variance < 6) {
            curv = Curvature.FLAT;
        } else if (diag1Diff > 3 && diag2Diff > 3) {
            curv = Curvature.CONVEX;
        } else if (diag1Diff < -3 && diag2Diff < -3) {
            curv = Curvature.CONCAVE;
        } else if (Math.abs(diag1Diff - diag2Diff) > 8) {
            curv = Curvature.SADDLE;
        } else {
            curv = Curvature.FLAT;
        }

        return new TerrainTopology(min, max, variance, slopeMag, asp, curv, cliff);
    }
}
