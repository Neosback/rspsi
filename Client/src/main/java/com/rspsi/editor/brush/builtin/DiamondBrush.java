package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;

import java.util.Set;

public final class DiamondBrush implements EditorBrush {
    @Override public String id() { return "diamond"; }
    @Override public String name() { return "Diamond"; }
    @Override public String description() { return "Hard-edged Manhattan-distance diamond footprint."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.TILE_PAINT);
    }
    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        return Math.abs(dx) + Math.abs(dy) <= r ? 1.0 : 0.0;
    }
}
