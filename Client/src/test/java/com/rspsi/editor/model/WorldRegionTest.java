package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldRegionTest {
    @Test
    void retainsRegionIdentityAndWorldOrigin() {
        WorldRegion region = new WorldRegion(50, 75, new WorldDocument(64, 64, 4));

        assertEquals((50 << 8) | 75, region.regionId());
        assertEquals(3200, region.window().originX());
        assertEquals(4800, region.window().originY());
    }

    @Test
    void rejectsNonRegionDocuments() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(1, 1, new WorldDocument(32, 64, 4)));
    }
}
