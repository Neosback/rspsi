package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;

import java.util.Set;

public final class SquareBrush implements EditorBrush {
    @Override public String id() { return "square"; }
    @Override public String name() { return "Square"; }
    @Override public String description() { return "Hard-edged square footprint."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.TILE_PAINT);
    }
    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        return Math.abs(dx) <= r && Math.abs(dy) <= r ? 1.0 : 0.0;
    }
}
