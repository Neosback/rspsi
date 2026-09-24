package com.rspsi.editor.render;

/** Frontend-only presentation applied after OSRS scene colors are resolved. */
public record RenderPresentation(double brightness, double exposure, boolean wireframe,
                                 boolean smoothBanding, int fogDepthTiles, int fogColor,
                                 GpuDebugView debugView) {
    /** Compatibility constructor for callers that only control exposure. */
    public RenderPresentation(double brightness, double exposure) {
        this(brightness, exposure, false, true, 0, 0x101827, GpuDebugView.NONE);
    }

    /** Compatibility constructor before the HSL interpolation mode was exposed. */
    public RenderPresentation(double brightness, double exposure, boolean wireframe) {
        this(brightness, exposure, wireframe, true, 0, 0x101827, GpuDebugView.NONE);
    }

    /** Compatibility constructor before fog presentation was exposed. */
    public RenderPresentation(double brightness, double exposure, boolean wireframe,
                              boolean smoothBanding) {
        this(brightness, exposure, wireframe, smoothBanding, 0, 0x101827, GpuDebugView.NONE);
    }

    /** Compatibility constructor before GPU debug presentation was exposed. */
    public RenderPresentation(double brightness, double exposure, boolean wireframe,
                              boolean smoothBanding, int fogDepthTiles, int fogColor) {
        this(brightness, exposure, wireframe, smoothBanding,
                fogDepthTiles, fogColor, GpuDebugView.NONE);
    }

    public RenderPresentation {
        if (!Double.isFinite(brightness) || brightness < 0.0) {
            throw new IllegalArgumentException("Brightness must be finite and non-negative");
        }
        if (!Double.isFinite(exposure)) {
            throw new IllegalArgumentException("Exposure must be finite");
        }
        if (fogDepthTiles < 0 || fogColor < 0 || fogColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Invalid fog presentation values");
        }
        debugView = java.util.Objects.requireNonNull(debugView, "debugView");
    }

    public static RenderPresentation neutral() {
        return new RenderPresentation(
                1.0, 0.0, false, true, 0, 0x101827, GpuDebugView.NONE);
    }

    public int apply(int channel) {
        double multiplier = brightness * Math.pow(2.0, exposure);
        return (int) Math.round(Math.max(0.0, Math.min(255.0, channel * multiplier)));
    }
}
