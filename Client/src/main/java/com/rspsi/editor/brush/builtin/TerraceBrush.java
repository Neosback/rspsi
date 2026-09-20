package com.rspsi.editor.brush.builtin;

import com.rspsi.editor.brush.BrushCapability;
import com.rspsi.editor.brush.HeightBrush;
import com.rspsi.editor.brush.HeightBrushContext;

import java.util.Set;

/** Quantizes terrain heights into configurable steps. */
public final class TerraceBrush implements HeightBrush {
    @Override public String id() { return "terrace"; }
    @Override public String name() { return "Terrace"; }
    @Override public String description() { return "Stepped height quantization brush."; }
    @Override public Set<BrushCapability> capabilities() {
        return Set.of(BrushCapability.SPATIAL_FOOTPRINT, BrushCapability.HEIGHT_MANIPULATION);
    }

    @Override public double weight(int dx, int dy, int radius) {
        int r = Math.max(0, radius);
        return dx * dx + dy * dy <= r * r ? 1.0 : 0.0;
    }

    @Override
    public int evaluateHeight(int currentHeight, int anchorHeight, double weight, HeightBrushContext context) {
        int step = Math.max(2, Math.min(96, context.terraceStep()));
        int quantized = Math.round((float) currentHeight / step) * step;
        return currentHeight + (int) Math.round((quantized - currentHeight) * weight);
    }
}
