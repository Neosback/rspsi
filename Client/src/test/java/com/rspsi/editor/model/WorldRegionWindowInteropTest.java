package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorldRegionWindowInteropTest {

    @Test
    void remainsPublicFinalWithPublicConstructorAndAccessors() throws Exception {
        assertTrue(Modifier.isPublic(WorldRegionWindow.class.getModifiers()));
        assertTrue(Modifier.isFinal(WorldRegionWindow.class.getModifiers()));
        assertTrue(Modifier.isPublic(
                WorldRegionWindow.class
                        .getConstructor(
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                Map.class)
                        .getModifiers()));

        WorldRegionWindow window =
                new WorldRegionWindow(10, 20, 2, 3, Map.of());

        assertEquals(10, window.minRegionX());
        assertEquals(20, window.minRegionY());
        assertEquals(2, window.regionWidth());
        assertEquals(3, window.regionHeight());
        assertEquals(6, window.expectedRegionCount());
        assertEquals(0, window.loadedRegionCount());
        assertFalse(window.complete());
    }

    @Test
    void preservesValidationOrderAndExplicitRegionsNullFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRegionWindow(-1, 0, 1, 1, null));

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new WorldRegionWindow(0, 0, 1, 1, null));
        assertEquals("regions", failure.getMessage());
    }

    @Test
    void rejectsOutsideAndDuplicateRegionValues() {
        WorldRegion outside = region(2, 0, 1);

        IllegalArgumentException outsideFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRegionWindow(
                        0, 0, 1, 1,
                        Map.of(outside.regionId(), outside)));
        assertEquals(
                "Region lies outside the requested window: " + outside.regionId(),
                outsideFailure.getMessage());

        WorldRegion duplicateA = region(0, 0, 1);
        WorldRegion duplicateB = region(0, 0, 1);
        Map<Integer, WorldRegion> duplicatedValues = new LinkedHashMap<>();
        duplicatedValues.put(1, duplicateA);
        duplicatedValues.put(2, duplicateB);

        IllegalArgumentException duplicateFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRegionWindow(0, 0, 1, 1, duplicatedValues));
        assertEquals("Duplicate region 0", duplicateFailure.getMessage());
    }

    @Test
    void regionMapAndMissingSetRemainUnmodifiable() {
        WorldRegion loaded = region(10, 20, 1);
        WorldRegionWindow window =
                new WorldRegionWindow(
                        10, 20, 2, 1,
                        Map.of(loaded.regionId(), loaded));

        assertThrows(UnsupportedOperationException.class,
                () -> window.regions().clear());

        Set<Integer> missing = window.missingRegionIds();
        assertEquals(Set.of((11 << 8) | 20), missing);
        assertThrows(UnsupportedOperationException.class,
                () -> missing.add(123));
    }

    @Test
    void copyIsDeepAtDocumentAndTileLevel() {
        WorldRegion loaded = region(10, 20, 1);
        loaded.document().tile(0, 1, 2).restore(new TileSnapshot(
                10, 20, 30, 40,
                1, 0, 0, 0, 0,
                java.util.List.of()));

        WorldRegionWindow source =
                new WorldRegionWindow(
                        10, 20, 1, 1,
                        Map.of(loaded.regionId(), loaded));
        WorldRegionWindow copy = source.copy();

        WorldRegion copiedRegion = copy.region(10, 20).orElseThrow();
        assertNotSame(loaded, copiedRegion);
        assertNotSame(loaded.document(), copiedRegion.document());
        assertNotSame(
                loaded.document().tile(0, 1, 2),
                copiedRegion.document().tile(0, 1, 2));
        assertEquals(
                loaded.document().tile(0, 1, 2).snapshot(),
                copiedRegion.document().tile(0, 1, 2).snapshot());

        copiedRegion.document().tile(0, 1, 2).restore(new TileSnapshot(
                99, 20, 30, 40,
                1, 0, 0, 0, 0,
                java.util.List.of()));
        assertEquals(10,
                loaded.document().tile(0, 1, 2).snapshot().southWestHeight());
    }

    @Test
    void emptyWindowMaterializationUsesDefaultPlanesAndNegativeBorderStillFails() {
        WorldRegionWindow window =
                new WorldRegionWindow(10, 20, 1, 1, Map.of());

        WorldDocument materialized = window.materializeWorldDocument();
        assertEquals(64, materialized.width());
        assertEquals(64, materialized.length());
        assertEquals(WorldDocument.DEFAULT_PLANES, materialized.planes());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> window.materializePaddedWorldDocument(-1));
        assertEquals(
                "World context border cannot be negative",
                failure.getMessage());
    }

    private static WorldRegion region(int x, int y, int planes) {
        return new WorldRegion(
                x,
                y,
                new WorldDocument(
                        WorldRegion.REGION_SIZE,
                        WorldRegion.REGION_SIZE,
                        planes));
    }
}
