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
    void texturedOverlayColourIsTheBareClampedLight() {
        // runescape-client class470: a textured overlay's colour is -1, and
        // the light adjustment turns it into a 7-bit light with no hue or
        // saturation. The texture supplies colour at raster time.
        assertEquals(96, OsrsTerrainColorMath.adjustOverlayHslLight(-1, 96));
        assertEquals(2, OsrsTerrainColorMath.adjustOverlayHslLight(-1, 0));
        assertEquals(126, OsrsTerrainColorMath.adjustOverlayHslLight(-1, 400));
    }
}
