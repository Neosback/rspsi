package com.rspsi.editor.brush;

import com.rspsi.editor.brush.builtin.CheckerBrush;
import com.rspsi.editor.brush.builtin.CircleBrush;
import com.rspsi.editor.brush.builtin.DiamondBrush;
import com.rspsi.editor.brush.builtin.GaussianBrush;
import com.rspsi.editor.brush.builtin.SlopeBrush;
import com.rspsi.editor.brush.builtin.SquareBrush;
import com.rspsi.editor.brush.builtin.TerraceBrush;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Canonical neutral brush host and sampler used by editor tools. */
public final class BrushEngine {
    public enum Falloff { HARD, LINEAR, SMOOTH, GAUSSIAN }

    private final Map<String, EditorBrush> brushes = new LinkedHashMap<>();

    public BrushEngine() {
        register(new SquareBrush());
        register(new CircleBrush());
        register(new DiamondBrush());
        register(new CheckerBrush());
        register(new GaussianBrush());
        register(new SlopeBrush());
        register(new TerraceBrush());
        ServiceLoader.load(EditorBrush.class).forEach(this::register);
    }

    public synchronized void register(EditorBrush brush) {
        Objects.requireNonNull(brush, "brush");
        if (brush.id() == null || brush.id().isBlank()) {
            throw new IllegalArgumentException("brush id cannot be blank");
        }
        brushes.put(brush.id(), brush);
    }

    public synchronized EditorBrush brush(String id) {
        EditorBrush result = brushes.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown brush: " + id);
        return result;
    }

    public synchronized List<EditorBrush> brushes() {
        return List.copyOf(brushes.values());
    }

    public BrushMask sample(EditorBrush brush, int radius, TileCoordinate center, WorldDocument world) {
        List<BrushSampling.Sample> samples = BrushSampling.sample(brush, radius, center, world);
        int minX = center.x();
        int maxX = center.x();
        int minY = center.y();
        int maxY = center.y();
        for (BrushSampling.Sample sample : samples) {
            minX = Math.min(minX, sample.absolute().x());
            maxX = Math.max(maxX, sample.absolute().x());
            minY = Math.min(minY, sample.absolute().y());
            maxY = Math.max(maxY, sample.absolute().y());
        }
        return new BrushMask(center, radius, samples, new TileBounds(minX, minY, maxX, maxY));
    }

    public static double applyFalloff(Falloff falloff, double normalizedDistance) {
        double t = Math.max(0.0, Math.min(1.0, normalizedDistance));
        return switch (Objects.requireNonNull(falloff, "falloff")) {
            case HARD -> t <= 1.0 ? 1.0 : 0.0;
            case LINEAR -> 1.0 - t;
            case SMOOTH -> {
                double linear = 1.0 - t;
                yield linear * linear * (3.0 - 2.0 * linear);
            }
            case GAUSSIAN -> Math.exp(-2.5 * t * t);
        };
    }
}
