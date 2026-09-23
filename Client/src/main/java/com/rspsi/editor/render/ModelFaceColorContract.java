package com.rspsi.editor.render;

/**
 * Exact client ModelData.toModel face-color contract.
 *
 * <p>The client keeps two distinct color concepts for a model face:
 * the unlit/source Jagex HSL value and the three post-lighting face-color
 * slots consumed by Model rendering. Flat and skipped faces encode their
 * mode in colorC while colorB remains its zero-initialized client value.
 * Keeping that distinction explicit prevents renderer-friendly expansion
 * from silently changing the model API contract.</p>
 */
public final class ModelFaceColorContract {
    /** Client faceColors3 sentinel for a flat-shaded face. */
    public static final int FLAT_SENTINEL = -1;
    /** Client faceColors3 sentinel for a face omitted from normal shading. */
    public static final int SKIP_SENTINEL = -2;

    private ModelFaceColorContract() {
    }

    /**
     * Produces the three lit client face-color slots from one unlit source face.
     *
     * @param unlitColor post-recolor Jagex HSL source color
     * @param textured whether the face has a texture
     * @param renderType client ModelData face render type
     * @param lightA smooth light value at vertex A
     * @param lightB smooth light value at vertex B
     * @param lightC smooth light value at vertex C
     * @param flatLight face-normal light value used by flat shading
     */
    public static LitFace shade(int unlitColor, boolean textured, int renderType,
                                int lightA, int lightB, int lightC, int flatLight) {
        if (textured) {
            if (renderType == 0) {
                return new LitFace(clampLight(lightA), clampLight(lightB), clampLight(lightC));
            }
            if (renderType == 1) {
                return new LitFace(clampLight(flatLight), 0, FLAT_SENTINEL);
            }
            return new LitFace(0, 0, SKIP_SENTINEL);
        }

        if (renderType == 1) {
            return new LitFace(blendLight(unlitColor, flatLight), 0, FLAT_SENTINEL);
        }
        if (renderType == 3) {
            return new LitFace(128, 0, FLAT_SENTINEL);
        }
        if (renderType == 2) {
            return new LitFace(0, 0, SKIP_SENTINEL);
        }
        return new LitFace(
                blendLight(unlitColor, lightA),
                blendLight(unlitColor, lightB),
                blendLight(unlitColor, lightC));
    }

    /** Client ModelData.method5263: preserve hue/saturation and relight luminance. */
    static int blendLight(int hsl, int lightness) {
        int light = (hsl & 127) * lightness >> 7;
        return (hsl & 0xFF80) + clamp(light, 2, 126);
    }

    /** Client ModelData.method5264: textured faces carry only a light scalar. */
    static int clampLight(int lightness) {
        return clamp(lightness, 2, 126);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Exact Model.faceColors1/2/3 values after ModelData lighting. */
    public record LitFace(int colorA, int colorB, int colorC) {
        public boolean flat() {
            return colorC == FLAT_SENTINEL;
        }

        public boolean skipped() {
            return colorC == SKIP_SENTINEL;
        }
    }
}
