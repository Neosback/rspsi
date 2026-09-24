package com.rspsi.editor.terrain.autotile;

/**
 * A continuous 2D area in world tile units (x east, y north; tile {@code (tx, ty)}
 * spans {@code [tx, tx+1] x [ty, ty+1]}). The autotiler samples it at shared
 * world points, so neighbouring tiles always see the same edge.
 */
@FunctionalInterface
public interface PathRegion {
    boolean contains(double x, double y);

    default PathRegion or(PathRegion other) {
        return (x, y) -> contains(x, y) || other.contains(x, y);
    }
}
