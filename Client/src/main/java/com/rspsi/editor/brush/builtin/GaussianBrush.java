package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.EditorBrush;

import java.util.Set;

public final class GaussianBrush implements EditorBrush {
    @Override public String id() { return "gaussian"; }
    @Override public String name() { return "Gaussian"; }
    @Override public String description() { return "Circular footprint with a smooth Gaussian falloff."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.WEIGHTED_FALLOFF, BrushCapability.TILE_PAINT);
    }
    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        if (r == 0) return dx == 0 && dy == 0 ? 1.0 : 0.0;
        double distance = Math.sqrt((double) dx * dx + (double) dy * dy);
        if (distance > r) return 0.0;
        double normalized = distance / (r + 0.5);
        return Math.exp(-2.5 * normalized * normalized);
    }
}
