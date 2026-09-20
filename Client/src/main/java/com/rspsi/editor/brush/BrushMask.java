package com.rspsi.editor.brush;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;

/** Immutable sampled brush footprint shared by preview and edit execution. */
public record BrushMask(
        TileCoordinate center,
        int radius,
        List<BrushSampling.Sample> samples,
        TileBounds bounds) {

    public BrushMask {
        Objects.requireNonNull(center, "center");
        if (radius < 0 || radius > 64) throw new IllegalArgumentException("radius must be 0..64");
        samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
        Objects.requireNonNull(bounds, "bounds");
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }
}
