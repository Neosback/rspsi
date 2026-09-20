package com.rspsi.editor.brush;

import com.rspsi.editor.model.DocumentCoordinates;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic conversion from a brush footprint to world and local tile samples. */
public final class BrushSampling {
    private BrushSampling() {
    }

    public record Sample(WorldTile world, LocalTile local, double weight) {
        public Sample {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(local, "local");
            if (!Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("Brush sample weight must be in (0, 1]");
            }
        }

        /** Compatibility alias retained while older callers migrate. */
        @Deprecated
        public WorldTile absolute() {
            return world;
        }
    }

    public static List<Sample> sample(EditorBrush brush, int radius,
                                      WorldTile worldCenter, WorldDocument world,
                                      WorldWindow window) {
        Objects.requireNonNull(brush, "brush");
        Objects.requireNonNull(worldCenter, "worldCenter");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(window, "window");
        if (radius < 0 || radius > 64) {
            throw new IllegalArgumentException("Brush radius must be 0 through 64");
        }

        DocumentCoordinates coordinates = new DocumentCoordinates(world, window);
        LocalTile center = coordinates.toLocal(worldCenter).orElse(null);
        if (center == null) return List.of();

        List<Sample> samples = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                double weight = brush.weight(dx, dy, radius);
                if (weight <= 0.0) continue;

                LocalTile local = new LocalTile(
                        center.plane(), center.x() + dx, center.y() + dy);
                if (!world.contains(local)) continue;

                samples.add(new Sample(
                        new WorldTile(worldCenter.plane(),
                                worldCenter.x() + dx, worldCenter.y() + dy),
                        local,
                        Math.min(1.0, weight)));
            }
        }
        return List.copyOf(samples);
    }
}
