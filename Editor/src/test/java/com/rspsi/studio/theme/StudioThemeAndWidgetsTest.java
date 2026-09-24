package com.rspsi.studio.theme;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class StudioThemeAndWidgetsTest {

    @Test
    void testStudioFontResourcesExist() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/font/Roboto-Regular.ttf")) {
            assertNotNull(is, "Roboto-Regular.ttf must exist in /font/");
            assertTrue(is.readAllBytes().length > 50_000, "Roboto-Regular.ttf size check");
        }

        try (InputStream is = getClass().getResourceAsStream("/font/fontawesome-webfont.ttf")) {
            assertNotNull(is, "fontawesome-webfont.ttf must exist in /font/");
            assertTrue(is.readAllBytes().length > 50_000, "fontawesome-webfont.ttf size check");
        }

        try (InputStream is = getClass().getResourceAsStream("/font/JetBrainsMono-Regular.ttf")) {
            assertNotNull(is, "JetBrainsMono-Regular.ttf must exist in /font/");
            assertTrue(is.readAllBytes().length > 50_000, "JetBrainsMono-Regular.ttf size check");
        }

        try (InputStream is = getClass().getResourceAsStream("/font/MaterialIcons-Regular.ttf")) {
            assertNotNull(is, "MaterialIcons-Regular.ttf must exist in /font/");
            assertTrue(is.readAllBytes().length > 100_000, "MaterialIcons-Regular.ttf size check");
        }
    }

    @Test
    void testStudioFontsConfiguration() {
        assertEquals(17.0f, StudioFonts.BASE_FONT_SIZE, "Main UI font size should remain readable at 17px");
    }

    @Test
    void testMaterialDesignIconsMapping() {
        assertNotNull(StudioIcons.MAP);
        assertNotNull(StudioIcons.BRUSH);
        assertNotNull(StudioIcons.PALETTE);
        assertNotNull(StudioIcons.OBJECT);
        assertNotNull(StudioIcons.SETTINGS);
        assertNotNull(StudioIcons.SEARCH);
        assertNotNull(StudioIcons.PIN);
        assertNotNull(StudioIcons.HEIGHT);
        assertNotNull(StudioIcons.TERRAIN);
        assertNotNull(StudioIcons.CHECK);
        assertNotNull(StudioIcons.CLOSE);
    }
}
