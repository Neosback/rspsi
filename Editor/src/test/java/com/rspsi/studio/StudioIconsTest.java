package com.rspsi.studio;

import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class StudioIconsTest {

    @Test
    void testMaterialIconFontResourceExists() throws Exception {
        try (InputStream is = StudioFonts.class.getResourceAsStream("/font/MaterialIcons-Regular.ttf")) {
            assertNotNull(is, "MaterialIcons-Regular.ttf must exist in /font/");
            byte[] bytes = is.readAllBytes();
            assertTrue(bytes.length > 100_000, "Font file must be larger than 100KB, got: " + bytes.length);
        }
    }

    @Test
    void testMaterialIconsCodepointsRegistry() {
        assertTrue(StudioIcons.registeredIconCount() > 2000,
                "Expected over 2000 icons loaded from codepoints, got: " + StudioIcons.registeredIconCount());

        assertTrue(StudioIcons.has("search"));
        assertTrue(StudioIcons.has("brush"));
        assertTrue(StudioIcons.has("palette"));
        assertTrue(StudioIcons.has("terrain"));
        assertTrue(StudioIcons.has("water_drop"));
        assertTrue(StudioIcons.has("layers"));
        assertTrue(StudioIcons.has("visibility"));
        assertTrue(StudioIcons.has("settings"));
        assertTrue(StudioIcons.has("push_pin"));
        assertTrue(StudioIcons.has("history"));
        assertTrue(StudioIcons.has("sports_esports"));
    }

    @Test
    void testSemanticIconConstants() {
        assertNotNull(StudioIcons.SEARCH);
        assertEquals("\ue8b6", StudioIcons.SEARCH);

        assertNotNull(StudioIcons.BRUSH);
        assertEquals("\ue3ae", StudioIcons.BRUSH);

        assertNotNull(StudioIcons.PALETTE);
        assertEquals("\ue40a", StudioIcons.PALETTE);

        assertNotNull(StudioIcons.TERRAIN);
        assertEquals("\ue564", StudioIcons.TERRAIN);

        assertNotNull(StudioIcons.WATER);
        assertEquals("\ue798", StudioIcons.WATER);

        assertNotNull(StudioIcons.OBJECT);
        assertEquals("\ue9fe", StudioIcons.OBJECT);

        assertNotNull(StudioIcons.SETTINGS);
        assertEquals("\ue8b8", StudioIcons.SETTINGS);

        assertNotNull(StudioIcons.PIN);
        assertEquals("\uf10d", StudioIcons.PIN);

        assertNotNull(StudioIcons.CLOSE);
        assertEquals("\ue5cd", StudioIcons.CLOSE);

        // Shape icons
        assertNotNull(StudioIcons.SHAPE_SQUARE);
        assertEquals("\ue3c6", StudioIcons.SHAPE_SQUARE);
        assertNotNull(StudioIcons.SHAPE_CIRCLE);
        assertEquals("\uef4a", StudioIcons.SHAPE_CIRCLE);
        assertNotNull(StudioIcons.SHAPE_DIAMOND);
        assertEquals("\uead5", StudioIcons.SHAPE_DIAMOND);
        assertNotNull(StudioIcons.SHAPE_FALLOFF);
        assertEquals("\ue3e9", StudioIcons.SHAPE_FALLOFF);

        // Height sculpting icons
        assertNotNull(StudioIcons.RAISE);
        assertEquals("\ue5d8", StudioIcons.RAISE);
        assertNotNull(StudioIcons.LOWER);
        assertEquals("\ue5db", StudioIcons.LOWER);
        assertNotNull(StudioIcons.FLATTEN);
        assertEquals("\uf108", StudioIcons.FLATTEN);
        assertNotNull(StudioIcons.TERRACE);
        assertEquals("\uf1a9", StudioIcons.TERRACE);

        // Math & Stepper icons
        assertNotNull(StudioIcons.ADD);
        assertEquals("\ue145", StudioIcons.ADD);
        assertNotNull(StudioIcons.REMOVE);
        assertEquals("\ue15b", StudioIcons.REMOVE);
        assertNotNull(StudioIcons.ROTATE_LEFT);
        assertEquals("\ue419", StudioIcons.ROTATE_LEFT);
        assertNotNull(StudioIcons.ROTATE_RIGHT);
        assertEquals("\ue41a", StudioIcons.ROTATE_RIGHT);
    }

    @Test
    void testByNameLookupAndFallback() {
        assertEquals("\ue8b6", StudioIcons.byName("search"));
        assertEquals("\ue3ae", StudioIcons.byName("brush"));
        assertEquals("\ue798", StudioIcons.byName("water_drop"));

        // Fallback behavior
        assertEquals(StudioIcons.OBJECT, StudioIcons.byName("non_existent_icon_xyz"));
        assertEquals("CUSTOM_FALLBACK", StudioIcons.byName("non_existent_icon_xyz", "CUSTOM_FALLBACK"));
    }
}
