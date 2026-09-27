package com.rspsi.studio.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudioDrawColorsTest {

    @Test
    void convertsArgbToImDrawListAbgrWithoutChangingAlphaOrGreen() {
        assertEquals(0xFF332211, StudioDrawColors.abgr(0xFF112233));
        assertEquals(0x80443322, StudioDrawColors.abgr(0x80223344));
    }

    @Test
    void conversionIsItsOwnInverse() {
        int argb = 0x7F168CFF;
        assertEquals(argb, StudioDrawColors.abgr(StudioDrawColors.abgr(argb)));
    }

    @Test
    void paletteKeepsTheExistingJavaStaticConstantsAndDrawHelper() {
        assertEquals(0xFF00CED1, StudioPalette.ACCENT);
        assertEquals(0xFF0B0F14, StudioPalette.APP_BG);
        assertEquals(
                StudioDrawColors.abgr(StudioPalette.ACCENT),
                StudioPalette.draw(StudioPalette.ACCENT));
        assertEquals(
                StudioDrawColors.abgr(StudioPalette.ACCENT),
                StudioPalette.u32(StudioPalette.ACCENT));
    }
}
