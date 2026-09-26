package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldRegionTest {

    @Test
    void remainsJvmRecordAndRetainsRegionIdentityAndWorldOrigin() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        WorldRegion region = new WorldRegion(50, 75, document);

        assertTrue(WorldRegion.class.isRecord());
        assertEquals(64, WorldRegion.REGION_SIZE);
        assertEquals(50, region.regionX());
        assertEquals(75, region.regionY());
        assertSame(document, region.document());
        assertEquals((50 << 8) | 75, region.regionId());

        WorldWindow window = region.window();
        assertEquals(3200, window.originX());
        assertEquals(4800, window.originY());
        assertEquals(64, window.width());
        assertEquals(64, window.length());

        assertEquals(new WorldRegion(50, 75, document), region);
        assertTrue(region.toString().startsWith("WorldRegion["));
    }

    @Test
    void acceptsTheFullUnsignedByteRegionCoordinateDomain() {
        assertDoesNotThrow(() ->
                new WorldRegion(0, 0, new WorldDocument(64, 64, 1)));
        WorldRegion max =
                new WorldRegion(255, 255, new WorldDocument(64, 64, 1));

        assertEquals(0xFFFF, max.regionId());
        assertEquals(255 * 64, max.window().originX());
        assertEquals(255 * 64, max.window().originY());
    }

    @Test
    void rejectsOutOfRangeRegionCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(-1, 0, new WorldDocument(64, 64, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(0, -1, new WorldDocument(64, 64, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(256, 0, new WorldDocument(64, 64, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(0, 256, new WorldDocument(64, 64, 1)));
    }

    @Test
    void rejectsNullAndNonRegionDocuments() {
        assertThrows(NullPointerException.class,
                () -> new WorldRegion(1, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(1, 1, new WorldDocument(32, 64, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldRegion(1, 1, new WorldDocument(64, 32, 4)));
    }

    @Test
    void windowPreservesLocalVsWorldCoordinateBoundary() {
        WorldRegion region =
                new WorldRegion(50, 75, new WorldDocument(64, 64, 4));
        LocalTile local = new LocalTile(2, 5, 7);

        WorldTile world = region.window().toWorld(local);

        assertEquals(2, world.plane());
        assertEquals(3205, world.x());
        assertEquals(4807, world.y());
        assertEquals(local, region.window().toLocal(world));
    }
}
