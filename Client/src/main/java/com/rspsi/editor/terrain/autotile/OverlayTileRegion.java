package com.rspsi.editor.terrain.autotile;

/**
 * The area already painted by existing path tiles, read back from their overlay
 * shapes. A point on a tile border is inside if any tile touching it covers it
 * there, so an existing path's open edges are seen the same from both sides.
 */
public final class OverlayTileRegion implements PathRegion {
    /** The existing path overlay at a tile, or {@code null} when the tile is not part of the path. */
    @FunctionalInterface
    public interface Lookup {
        OverlayShapeAtlas.Entry pathShapeAt(int tileX, int tileY);
    }

    private static final double NUDGE = 1e-3;
    private final Lookup lookup;

    public OverlayTileRegion(Lookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public boolean contains(double x, double y) {
        int tx = (int) Math.floor(x);
        int ty = (int) Math.floor(y);
        boolean onX = x == tx;
        boolean onY = y == ty;
        for (int dx = onX ? -1 : 0; dx <= 0; dx++) {
            for (int dy = onY ? -1 : 0; dy <= 0; dy++) {
                OverlayShapeAtlas.Entry entry = lookup.pathShapeAt(tx + dx, ty + dy);
                if (entry == null) continue;
                double lx = x - (tx + dx);
                double ly = y - (ty + dy);
                lx = Math.min(1.0 - NUDGE, Math.max(NUDGE, lx));
                ly = Math.min(1.0 - NUDGE, Math.max(NUDGE, ly));
                if (entry.covers(lx, ly)) return true;
            }
        }
        return false;
    }
}
