package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;

import java.util.Set;

public final class CheckerBrush implements EditorBrush {
    @Override public String id() { return "checker"; }
    @Override public String name() { return "Checker"; }
    @Override public String description() { return "Alternating checkerboard samples inside a square footprint."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.TILE_PAINT);
    }
    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        if (Math.abs(dx) > r || Math.abs(dy) > r) return 0.0;
        return ((Math.abs(dx) + Math.abs(dy)) & 1) == 0 ? 1.0 : 0.0;
    }
}
