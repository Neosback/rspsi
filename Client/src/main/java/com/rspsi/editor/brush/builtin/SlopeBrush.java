package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.HeightBrush;
import com.rspsi.editor.brush.HeightBrushContext;

import java.util.Set;

/** Directional height evaluator for ramp-like sculpting. */
public final class SlopeBrush implements HeightBrush {
    @Override public String id() { return "slope"; }
    @Override public String name() { return "Slope"; }
    @Override public String description() { return "Directional ramp height brush."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.HEIGHT_MANIPULATION);
    }

    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        return Math.abs(dx) <= r && Math.abs(dy) <= r ? 1.0 : 0.0;
    }

    @Override
    public int evaluateHeight(int currentHeight, int anchorHeight, double weight, HeightBrushContext context) {
        double directionX = Math.cos(context.directionRadians());
        double directionY = Math.sin(context.directionRadians());
        double projected = context.dx() * directionX + context.dy() * directionY;
        double span = Math.max(1.0, context.radius());
        double normalized = Math.max(-1.0, Math.min(1.0, projected / span));
        return anchorHeight + (int) Math.round(normalized * context.strength() * weight);
    }
}
