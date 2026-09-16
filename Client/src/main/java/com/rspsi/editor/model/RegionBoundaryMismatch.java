package com.rspsi.editor.model;

/** One height discontinuity across two adjacent loaded OSRS regions. */
public record RegionBoundaryMismatch(
        RegionBoundaryDirection direction,
        int plane,
        int regionX,
        int regionY,
        int alongEdge,
        boolean upperCorner,
        int expected,
        int actual
) {
    public RegionBoundaryMismatch {
        if (plane < 0 || regionX < 0 || regionY < 0 || alongEdge < 0 || alongEdge >= 64) {
            throw new IllegalArgumentException("Invalid region boundary location");
        }
        if (direction == null) {
            throw new NullPointerException("direction");
        }
    }
}
