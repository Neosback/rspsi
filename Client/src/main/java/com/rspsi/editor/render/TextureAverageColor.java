package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the packed OSRS HSL of a cache texture's own average colour.
 *
 * <p>Textured floors have no author-supplied hue and saturation: the scene
 * stores the {@code -1} render-HSL sentinel for a textured overlay and the
 * client derives the tile colour from the texture. Packing a bare light
 * instead - the previous behaviour - leaves both the hue and the saturation
 * bits at zero, so every texel resolves through the grey axis of the palette
 * and textured floors (water, interior floors, paths) render flat grey
 * regardless of the texture they use.</p>
 *
 * <p>The OpenRune 3.0.2 texture definition exposes only its 16-bit
 * {@code averageRgb} record field, which is not a packed RGB colour (the
 * revision-240 records carry values such as {@code 0x0071AD} whose channels do
 * not describe the decoded sprite), and reports {@code averageHsl} as
 * unavailable. The average is therefore computed from the decoded texture
 * pixels exactly as the client's {@code Texture.averageTextureColour()} does,
 * and converted with the client's own RGB to HSL maths
 * ({@code Floor.rgbToHsl} followed by the packed-HSL reduction).</p>
 *
 * <p>One instance caches per texture id and is shared per definition provider,
 * so each texture is decoded and averaged at most once per session.</p>
 */
public final class TextureAverageColor {
    /** Client texture decode contract: brightness 0.6, 128x128 texels. */
    private static final double CLIENT_TEXTURE_BRIGHTNESS = 0.6;
    private static final int CLIENT_TEXTURE_SIZE = 128;
    /** The client's transparent-cutout markers, skipped when averaging. */
    private static final int MAGENTA_SENTINEL = 0xFF00FF;
    private static final int GREEN_SENTINEL = 0x00FF00;

    private static final Map<DefinitionProvider, TextureAverageColor> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final DefinitionProvider definitions;
    private final Map<Integer, Integer> cache = new ConcurrentHashMap<>();

    private TextureAverageColor(DefinitionProvider definitions) {
        this.definitions = definitions;
    }

    /** Returns the shared resolver for one cache provider. */
    public static TextureAverageColor of(DefinitionProvider definitions) {
        Objects.requireNonNull(definitions, "definitions");
        return INSTANCES.computeIfAbsent(definitions, TextureAverageColor::new);
    }

    /**
     * Returns the texture's average colour as packed OSRS HSL, or {@code -1}
     * when the provider cannot decode that texture.
     */
    public int packedHsl(int textureId) {
        if (textureId < 0) return -1;
        return cache.computeIfAbsent(textureId, this::resolve);
    }

    private int resolve(int textureId) {
        int[] pixels;
        try {
            pixels = definitions.texturePixels(textureId, CLIENT_TEXTURE_BRIGHTNESS,
                    CLIENT_TEXTURE_SIZE).orElse(null);
        } catch (RuntimeException failure) {
            return -1;
        }
        if (pixels == null || pixels.length == 0) return -1;
        int rgb = averageRgb(pixels);
        return rgb < 0 ? -1 : packedHslFromRgb(rgb);
    }

    /**
     * Averages the texture's visible texels, or returns {@code -1} when it has
     * none.
     *
     * <p>The client's cutout markers and its transparent texels are skipped: a
     * texture can be mostly transparent (texture 12 is about a third opaque)
     * and averaging its holes would drag the hue and saturation the tile
     * colour is derived from toward black. A texture that is transparent
     * throughout has no colour to report, so the caller keeps the previous
     * light-only behaviour instead of claiming a black material.</p>
     */
    static int averageRgb(int[] pixels) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;
        for (int pixel : pixels) {
            int rgb = pixel & 0xFFFFFF;
            if (rgb == MAGENTA_SENTINEL || rgb == GREEN_SENTINEL) continue;
            boolean transparent = (pixel >>> 24 & 0xFF) == 0 && rgb == 0;
            if (transparent) continue;
            red += rgb >> 16 & 0xFF;
            green += rgb >> 8 & 0xFF;
            blue += rgb & 0xFF;
            count++;
        }
        if (count == 0) return -1;
        return (int) (red / count) << 16 | (int) (green / count) << 8 | (int) (blue / count);
    }

    /**
     * Converts RGB to packed OSRS HSL using the client's floor-colour maths.
     *
     * <p>The hue and lightness are quantised to the palette's six and seven
     * bit fields, and saturation keeps the client's high-luminance reduction
     * that {@link OsrsTerrainColorMath#packHsl(int, int, int)} applies.</p>
     */
    static int packedHslFromRgb(int rgb) {
        double r = (rgb >> 16 & 0xFF) / 256.0;
        double g = (rgb >> 8 & 0xFF) / 256.0;
        double b = (rgb & 0xFF) / 256.0;
        double min = Math.min(r, Math.min(g, b));
        double max = Math.max(r, Math.max(g, b));
        double lightness = (min + max) / 2.0;
        double hue = 0.0;
        double saturation = 0.0;
        if (min != max) {
            saturation = lightness < 0.5
                    ? (max - min) / (max + min)
                    : (max - min) / (2.0 - max - min);
            if (r == max) {
                hue = (g - b) / (max - min);
            } else if (g == max) {
                hue = 2.0 + (b - r) / (max - min);
            } else {
                hue = 4.0 + (r - g) / (max - min);
            }
        }
        hue /= 6.0;
        if (hue < 0.0) hue += 1.0;
        if (hue >= 1.0) hue -= 1.0;
        int hueByte = (int) Math.min(255.0, hue * 256.0);
        int saturationByte = clampByte((int) (saturation * 256.0));
        int lightnessByte = clampByte((int) (lightness * 256.0));
        return OsrsTerrainColorMath.packHsl(hueByte, saturationByte, lightnessByte);
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
