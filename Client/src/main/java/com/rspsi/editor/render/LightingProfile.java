package com.rspsi.editor.render;

/**
 * Renderer-neutral OSRS lighting inputs. Values are kept in the integer
 * domain used by the client so reference packets remain deterministic.
 */
public record LightingProfile(
        int lightX,
        int lightY,
        int lightZ,
        int ambient,
        int intensityFactor,
        int heightScale,
        int lightMagnitude,
        boolean objectOcclusion,
        boolean mergeModelNormals,
        double textureGamma
) {
    public LightingProfile {
        if (lightMagnitude <= 0 || intensityFactor <= 0 || heightScale <= 0) {
            throw new IllegalArgumentException("Lighting scale values must be positive");
        }
        if (!Double.isFinite(textureGamma) || textureGamma <= 0.0) {
            throw new IllegalArgumentException("Texture gamma must be finite and positive");
        }
    }

    /** Default directional profile used by RuneLite/TSPS-style OSRS scenes. */
    public static LightingProfile osrs() {
        return new LightingProfile(-50, -10, -50, 96, 768, 65536, 71,
                true, true, 0.6);
    }

    public int lightIntensity() {
        return (lightMagnitude * intensityFactor) >> 8;
    }
}
