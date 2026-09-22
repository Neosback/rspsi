package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsrsTerrainColorMathTest {
    @Test
    void convertsPackedHslWithTheClientPaletteFormula() {
        assertEquals(0x887878,
                OsrsTerrainColorMath.packedHslToRgb(64, 1.0));
        assertEquals(0xF41207,
                OsrsTerrainColorMath.packedHslToRgb(
                        OsrsTerrainColorMath.packHsl(0, 255, 127), 1.0));
    }

    @Test
    void malformedAndInvalidHslUseTheClientInvalidColour() {
        assertEquals(OsrsTerrainColorMath.INVALID_HSL_COLOR,
                OsrsTerrainColorMath.packedHslToRgb(-1, 0.6));
        assertEquals(OsrsTerrainColorMath.INVALID_HSL_COLOR,
                OsrsTerrainColorMath.packedHslToRgb(OsrsTerrainColorMath.INVALID_HSL_COLOR, 0.6));
        assertThrows(IllegalArgumentException.class,
                () -> OsrsTerrainColorMath.packedHslToRgb(64, 0.0));
    }

    @Test
    void texturedOverlayKeepsTextureHueAndSaturationAndUsesTheTileLight() {
        // A blue-water texture average at half saturation.
        int textureAverage = OsrsTerrainColorMath.packHsl(176, 128, 90);

        int packed = OsrsTerrainColorMath.texturedOverlayHsl(textureAverage, 96);

        // Hue and saturation come from the texture, so the palette lookup is
        // no longer stuck on the greyscale axis...
        assertEquals(textureAverage & 0xFF80, packed & 0xFF80);
        // ...and the lightness slot carries the tile light, not the texture's
        // own average brightness, so the texture cannot darken itself twice.
        assertEquals(96, packed & 0x7F);
    }

    @Test
    void texturedOverlayClampsTheTileLightIntoTheUsableRange() {
        int textureAverage = OsrsTerrainColorMath.packHsl(176, 128, 90);

        assertEquals(2, OsrsTerrainColorMath.texturedOverlayHsl(textureAverage, 0) & 0x7F);
        assertEquals(126, OsrsTerrainColorMath.texturedOverlayHsl(textureAverage, 400) & 0x7F);
    }
}
