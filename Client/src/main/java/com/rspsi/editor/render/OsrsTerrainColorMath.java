package com.rspsi.editor.render;

/**
 * Client-compatible color operations used while deriving OSRS terrain.
 *
 * <p>Floor definitions expose hue, saturation, and luminance in the
 * 0..255 source domain. The client reduces those values to the packed
 * 6/3/7-bit HSL representation only after blending underlays. Keeping this
 * conversion in one named utility prevents scene and minimap code from
 * accidentally treating packed values as source values.</p>
 */
public final class OsrsTerrainColorMath {
    /** RuneLite/TSPS sentinel used for an invalid or hidden terrain color. */
    public static final int INVALID_HSL_COLOR = 12_345_678;

    private OsrsTerrainColorMath() {
    }

    /**
     * Packs source-domain HSL using the same saturation reduction as the
     * classic OSRS floor-color path.
     */
    public static int packHsl(int hue, int saturation, int luminance) {
        int reducedSaturation = saturation;
        if (luminance > 179) {
            reducedSaturation /= 2;
        }
        if (luminance > 192) {
            reducedSaturation /= 2;
        }
        if (luminance > 217) {
            reducedSaturation /= 2;
        }
        if (luminance > 243) {
            reducedSaturation /= 2;
        }

        return (hue / 4 << 10) + (reducedSaturation / 32 << 7) + luminance / 2;
    }

    /**
     * Applies a per-corner terrain light to a packed HSL value.
     *
     * <p>The light value is deliberately not clamped before scaling. This is
     * the client behavior; only the resulting 7-bit luminance is clamped to
     * the usable 2..126 range.</p>
     */
    public static int adjustPackedHslLight(int packedHsl, int light) {
        int adjustedLuminance = (packedHsl & 0x7F) * light / 128;
        adjustedLuminance = Math.max(2, Math.min(126, adjustedLuminance));
        return (packedHsl & 0xFF80) + adjustedLuminance;
    }

    /**
     * Applies client overlay-light semantics. A textured overlay has no
     * packed render HSL, so its vertex value is the clamped light itself;
     * the -2 sentinel becomes the client's invalid/HSL-hole color.
     */
    public static int adjustOverlayHslLight(int packedHsl, int light) {
        int adjustedLight = Math.max(2, Math.min(126, light));
        if (packedHsl == -2) {
            return INVALID_HSL_COLOR;
        }
        if (packedHsl == -1) {
            return adjustedLight;
        }
        return adjustPackedHslLight(packedHsl, adjustedLight);
    }

    /** Matches the client packed-HSL midpoint used by shaped tile vertices. */
    public static int mixPackedHsl(int first, int second) {
        if (first < 0) return second;
        if (second < 0) return first;
        int hue = (((first >> 10) & 0x3F) + ((second >> 10) & 0x3F)) >> 1;
        int saturation = (((first >> 7) & 0x07) + ((second >> 7) & 0x07)) >> 1;
        int lightness = ((first & 0x7F) + (second & 0x7F)) >> 1;
        return (hue << 10) | (saturation << 7) | lightness;
    }

    /**
     * Converts a packed OSRS HSL palette entry to the RGB value used by the
     * legacy client's palette builder. This is a presentation conversion only;
     * scene packets continue to carry packed HSL so GPU backends can apply the
     * same palette or shader policy without losing authored data.
     *
     * @param packedHsl packed six/three/seven-bit HSL value
     * @param exponent client brightness exponent (the normal OSRS value is 0.6)
     * @return opaque RGB, or the client invalid-colour sentinel for malformed input
     */
    public static int packedHslToRgb(int packedHsl, double exponent) {
        if (!Double.isFinite(exponent) || exponent <= 0.0) {
            throw new IllegalArgumentException("Brightness exponent must be finite and positive");
        }
        if (packedHsl < 0 || packedHsl > 0xFFFF || packedHsl == INVALID_HSL_COLOR) {
            return INVALID_HSL_COLOR;
        }

        int hueBand = (packedHsl >> 10) & 0x3F;
        int saturationBand = (packedHsl >> 7) & 0x07;
        int lightness = packedHsl & 0x7F;
        double hue = hueBand / 64.0 + 0.0078125;
        double saturation = saturationBand / 8.0 + 0.0625;
        double luminance = lightness / 128.0;

        double red = luminance;
        double green = luminance;
        double blue = luminance;
        if (saturation != 0.0) {
            double upper = luminance < 0.5
                    ? luminance * (1.0 + saturation)
                    : luminance + saturation - luminance * saturation;
            double lower = 2.0 * luminance - upper;
            red = hueChannel(lower, upper, hue + 1.0 / 3.0);
            green = hueChannel(lower, upper, hue);
            blue = hueChannel(lower, upper, hue - 1.0 / 3.0);
        }

        int rgb = (clampChannel((int) (Math.pow(red, exponent) * 256.0)) << 16)
                | (clampChannel((int) (Math.pow(green, exponent) * 256.0)) << 8)
                | clampChannel((int) (Math.pow(blue, exponent) * 256.0));
        // Rasterizer3D_buildPalette reserves zero as the transparent/invalid
        // palette entry and rewrites a genuinely black result to one.
        return rgb == 0 ? 1 : rgb;
    }

    private static double hueChannel(double lower, double upper, double hue) {
        if (hue > 1.0) hue -= 1.0;
        if (hue < 0.0) hue += 1.0;
        if (6.0 * hue < 1.0) return lower + (upper - lower) * 6.0 * hue;
        if (2.0 * hue < 1.0) return upper;
        if (3.0 * hue < 2.0) return lower + (upper - lower) * (2.0 / 3.0 - hue) * 6.0;
        return lower;
    }

    private static int clampChannel(int channel) {
        return Math.max(0, Math.min(255, channel));
    }

}
