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
}
