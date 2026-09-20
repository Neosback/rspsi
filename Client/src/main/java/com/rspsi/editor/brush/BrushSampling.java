package com.rspsi.editor.brush;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic conversion from a brush footprint to absolute and local tile samples. */
public final class BrushSampling {
    private BrushSampling() {
    }

    public record Sample(TileCoordinate absolute, TileCoordinate local, double weight) {
        public Sample {
            Objects.requireNonNull(absolute, "absolute");
            Objects.requireNonNull(local, "local");
            if (!Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
                throw new IllegalArgumentException("Brush sample weight must be in (0, 1]");
            }
        }
    }

    public static List<Sample> sample(EditorBrush brush, int radius,
                                      TileCoordinate absoluteCenter, WorldDocument world) {
        Objects.requireNonNull(brush, "brush");
        Objects.requireNonNull(absoluteCenter, "absoluteCenter");
        Objects.requireNonNull(world, "world");
        if (radius < 0 || radius > 64) {
            throw new IllegalArgumentException("Brush radius must be 0 through 64");
        }

        int centerLocalX;
        int centerLocalY;
        if (world.contains(absoluteCenter)) {
            centerLocalX = absoluteCenter.x();
            centerLocalY = absoluteCenter.y();
        } else {
            WorldTileAddress address = WorldTileAddress.of(
                    absoluteCenter.x(), absoluteCenter.y(), absoluteCenter.plane());
            centerLocalX = address.regionLocalX();
            centerLocalY = address.regionLocalY();
            if (!world.contains(absoluteCenter.plane(), centerLocalX, centerLocalY)) {
                return List.of();
            }
        }
        List<Sample> samples = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                double weight = brush.weight(dx, dy, radius);
                if (weight <= 0.0) continue;

                int localX = centerLocalX + dx;
                int localY = centerLocalY + dy;
                if (!world.contains(absoluteCenter.plane(), localX, localY)) continue;

                samples.add(new Sample(
                        new TileCoordinate(absoluteCenter.plane(),
                                absoluteCenter.x() + dx, absoluteCenter.y() + dy),
                        new TileCoordinate(absoluteCenter.plane(), localX, localY),
                        Math.min(1.0, weight)));
            }
        }
        return List.copyOf(samples);
    }
}
