package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.TextureDefinitionView;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the texture-average colour that textured floors, textures on models
 * and the minimap all derive their hue and saturation from.
 */
class TextureAverageColorTest {

    private static final int WATER_TEAL = 0x62A992;

    @Test
    void averagesOnlyTheVisibleTexels() {
        int[] pixels = {
                0x203040, 0x405060, 0x000000, 0x000000, 0xFF00FF, 0x00FF00,
        };

        // The two black texels are the client's transparent cutout, and the
        // magenta/green pixels are its transparent markers: neither may drag
        // the hue of the visible texels toward black.
        assertEquals(0x304050, TextureAverageColor.averageRgb(pixels));
    }

    @Test
    void reportsNothingToAverageWhenEveryTexelIsTransparent() {
        // No visible texel means no colour to claim: the caller must keep the
        // light-only vertex value rather than assert a black material.
        assertEquals(-1, TextureAverageColor.averageRgb(new int[]{0, 0, 0xFF00FF}));
    }

    @Test
    void packedHslKeepsHueAndSaturationAndQuantisesThePaletteFields() {
        int white = TextureAverageColor.packedHslFromRgb(0xFFFFFF);
        assertEquals(0, white >> 10 & 0x3F, "white has no hue");
        assertEquals(0, white >> 7 & 0x07, "white has no saturation");

        int red = TextureAverageColor.packedHslFromRgb(0xFF0000);
        assertEquals(7, red >> 7 & 0x07, "pure red is fully saturated");
        assertEquals(0, red >> 10 & 0x3F, "pure red sits at hue zero");

        int teal = TextureAverageColor.packedHslFromRgb(WATER_TEAL);
        assertNotEquals(0, teal >> 10 & 0x3F, "water keeps its hue");
        assertNotEquals(0, teal >> 7 & 0x07, "water keeps its saturation");
    }

    @Test
    void derivesTheAverageFromDecodedPixelsWhenTheProviderHasNoAverageHsl() {
        int[] pixels = new int[16];
        java.util.Arrays.fill(pixels, WATER_TEAL);
        DefinitionProvider definitions = provider(pixels, new AtomicInteger());

        int packed = TextureAverageColor.of(definitions).packedHsl(25);

        assertTrue(packed > 0, "decoded pixels must yield a texture colour");
        assertEquals(TextureAverageColor.packedHslFromRgb(WATER_TEAL) & 0xFF80,
                packed & 0xFF80);
    }

    @Test
    void decodesEachTextureOncePerProvider() {
        AtomicInteger decodes = new AtomicInteger();
        DefinitionProvider definitions = provider(new int[]{0x203040}, decodes);
        TextureAverageColor resolver = TextureAverageColor.of(definitions);

        resolver.packedHsl(4);
        resolver.packedHsl(4);
        resolver.packedHsl(4);

        assertEquals(1, decodes.get());
    }

    @Test
    void reportsUnavailableWhenTheProviderCannotDecodeTheTexture() {
        DefinitionProvider definitions = provider(null, new AtomicInteger());

        assertEquals(-1, TextureAverageColor.of(definitions).packedHsl(4));
        assertEquals(-1, TextureAverageColor.of(definitions).packedHsl(-1));
    }

    private static DefinitionProvider provider(int[] pixels, AtomicInteger decodes) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<TextureDefinitionView> texture(int id) {
                if (id != 4 && id != 25) return Optional.empty();
                // The OpenRune 3.0.2 view reports the average HSL as
                // unavailable; only the decoded pixels carry the colour.
                return Optional.of(new TextureDefinitionView(
                        id, false, id, 0x001616, -1, 0, 0, false));
            }

            @Override public Optional<int[]> texturePixels(int id, double brightness, int size) {
                if (pixels == null) return Optional.empty();
                decodes.incrementAndGet();
                return Optional.of(pixels);
            }
        };
    }
}
