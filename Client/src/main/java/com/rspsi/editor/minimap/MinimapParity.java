package com.rspsi.editor.minimap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compares neutral minimap rasters for future external parity fixtures. */
public final class MinimapParity {
    private static final int MAX_REPORTED_PIXELS = 128;

    private MinimapParity() {
    }

    public static Report compare(MinimapImage expected, MinimapImage actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        List<PixelDifference> differences = new ArrayList<>();
        int differingPixels = 0;
        if (expected.plane() != actual.plane()) {
            differingPixels++;
        }
        int width = Math.min(expected.width(), actual.width());
        int height = Math.min(expected.height(), actual.height());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int expectedPixel = expected.pixel(x, y);
                int actualPixel = actual.pixel(x, y);
                if (expectedPixel != actualPixel) {
                    differingPixels++;
                    if (differences.size() < MAX_REPORTED_PIXELS) {
                        differences.add(new PixelDifference(x, y, expectedPixel, actualPixel));
                    }
                }
            }
        }
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            differingPixels++;
        }
        return new Report(expected.plane(), actual.plane(), expected.width(), expected.height(),
                actual.width(), actual.height(), differingPixels, differences);
    }

    public record PixelDifference(int x, int y, int expected, int actual) {
        public PixelDifference {
            if (x < 0 || y < 0) throw new IllegalArgumentException("Pixel coordinates cannot be negative");
        }
    }

    public record Report(int expectedPlane, int actualPlane,
                         int expectedWidth, int expectedHeight,
                         int actualWidth, int actualHeight,
                         int differingPixels, List<PixelDifference> differences) {
        public Report {
            if (expectedWidth <= 0 || expectedHeight <= 0 || actualWidth <= 0 || actualHeight <= 0
                    || differingPixels < 0) {
                throw new IllegalArgumentException("Invalid minimap parity dimensions");
            }
            differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
            if (differences.size() > MAX_REPORTED_PIXELS) {
                throw new IllegalArgumentException("Minimap parity report exceeds its difference limit");
            }
        }

        public boolean matches() {
            return differingPixels == 0;
        }

        public boolean truncated() {
            return differingPixels > differences.size();
        }
    }
}
