package com.rspsi.ui.workspace;

/** Persisted bounds for a detached workspace panel. */
public record WindowBounds(double x, double y, double width, double height) {
    public WindowBounds {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width < 240 || height < 160) {
            throw new IllegalArgumentException("Invalid detached window bounds");
        }
    }
}
