package com.rspsi.studio.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudioIconsRegistryTest {

    @Test
    void dynamicRegistryResolvesKnownMaterialIconNames() {
        assertTrue(StudioIcons.registeredIconCount() > 0);
        assertTrue(StudioIcons.has("brush"));
        assertEquals(StudioIcons.BRUSH, StudioIcons.byName("brush"));
        assertEquals(StudioIcons.BRUSH, StudioIcons.byName("  BRUSH  "));
    }

    @Test
    void lookupFallsBackForBlankOrUnknownNames() {
        assertEquals(StudioIcons.OBJECT, StudioIcons.byName("definitely_not_an_icon"));
        assertEquals("fallback", StudioIcons.byName("", "fallback"));
        assertEquals("fallback", StudioIcons.byName(null, "fallback"));
    }
}
