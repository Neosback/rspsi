package com.rspsi.editor.render;

/**
 * 2D screen coordinate produced by projecting a 3D world position onto the viewport.
 * If {@code visible} is false, the point lies behind the camera plane or outside the visible cone.
 */
public record ScreenPoint(float x, float y, boolean visible) {
    private static final ScreenPoint HIDDEN = new ScreenPoint(Float.NaN, Float.NaN, false);

    public static ScreenPoint of(float x, float y) {
        return new ScreenPoint(x, y, true);
    }

    public static ScreenPoint hidden() {
        return HIDDEN;
    }
}
