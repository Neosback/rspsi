package com.rspsi.editor.debug;

/** Frontend-neutral RGBA color used by diagnostics and marker projections. */
public record DebugColor(int red, int green, int blue, int alpha) {
    public DebugColor {
        if (red < 0 || red > 255 || green < 0 || green > 255
                || blue < 0 || blue > 255 || alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("Debug color channels must be in [0,255]");
        }
    }

    public int argb() {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    public static final DebugColor RED = new DebugColor(221, 44, 0, 255);
    public static final DebugColor GREEN = new DebugColor(0, 200, 83, 255);
    public static final DebugColor ORANGE = new DebugColor(255, 109, 0, 255);
    public static final DebugColor YELLOW = new DebugColor(255, 214, 0, 255);
    public static final DebugColor CYAN = new DebugColor(0, 184, 212, 255);
    public static final DebugColor BLUE = new DebugColor(41, 98, 255, 255);
    public static final DebugColor DEEP_PURPLE = new DebugColor(98, 0, 234, 255);
    public static final DebugColor PURPLE = new DebugColor(170, 0, 255, 255);
    public static final DebugColor GRAY = new DebugColor(158, 158, 158, 255);
    public static final DebugColor MARKER_FILL = new DebugColor(0, 0, 0, 50);
}
