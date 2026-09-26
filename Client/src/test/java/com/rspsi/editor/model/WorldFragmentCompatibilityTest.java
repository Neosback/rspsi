package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentCompatibilityTest {

    @Test
    void remainsJvmRecordWithHistoricalComponentAndStaticCaptureSurface() throws Exception {
        assertTrue(WorldFragment.class.isRecord());

        var components = WorldFragment.class.getRecordComponents();
        assertEquals(3, components.length);
        assertEquals("bounds", components[0].getName());
        assertEquals("terrain", components[1].getName());
        assertEquals("objects", components[2].getName());

        assertTrue(Modifier.isStatic(
                WorldFragment.class
                        .getMethod("capture", WorldDocument.class, TileBounds.class)
                        .getModifiers()));
    }

    @Test
    void canonicalConstructorPreservesNullNormalizationAndBoundsFailure() {
        TileBounds bounds = new TileBounds(1, 2, 1, 2);
        WorldFragment fragment = new WorldFragment(bounds, null, null);

        assertSame(bounds, fragment.bounds());
        assertEquals(List.of(), fragment.terrain());
        assertEquals(List.of(), fragment.objects());

        NullPointerException failure = assertThrows(
                NullPointerException.class,
                () -> new WorldFragment(null, null, null));
        assertEquals("bounds", failure.getMessage());
    }

    @Test
    void listsRemainDefensivelyCopiedAndUnmodifiable() {
        TileBounds bounds = new TileBounds(1, 1, 1, 1);
        List<TerrainTilePatch> terrain = new ArrayList<>();
        terrain.add(new TerrainTilePatch(
                0, 1, 1,
                new TileSnapshot(
                        1, 2, 3, 4,
                        5, 0, 0, 0, 0,
                        List.of())));
        List<WorldObject> objects = new ArrayList<>();
        objects.add(new WorldObject(42, 10, 0, 0, 1, 1));

        WorldFragment fragment = new WorldFragment(bounds, terrain, objects);

        terrain.clear();
        objects.clear();

        assertEquals(1, fragment.terrain().size());
        assertEquals(1, fragment.objects().size());
        assertThrows(UnsupportedOperationException.class,
                () -> fragment.terrain().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> fragment.objects().clear());
    }

    @Test
    void validationMessagesRemainStable() {
        TileBounds bounds = new TileBounds(2, 2, 2, 2);

        IllegalArgumentException terrainFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragment(
                        bounds,
                        List.of(new TerrainTilePatch(
                                0, 1, 2,
                                new TileSnapshot(
                                        0, 0, 0, 0,
                                        0, 0, 0, 0, 0,
                                        List.of()))),
                        List.of()));
        assertEquals(
                "Terrain patch is outside fragment bounds",
                terrainFailure.getMessage());

        IllegalArgumentException objectFailure = assertThrows(
                IllegalArgumentException.class,
                () -> new WorldFragment(
                        bounds,
                        List.of(),
                        List.of(new WorldObject(42, 10, 0, 0, 1, 2))));
        assertEquals(
                "Object is outside fragment bounds",
                objectFailure.getMessage());
    }

    @Test
    void capturePreservesPlaneOrderStripsTileObjectsAndDeduplicatesObjectOrder() {
        WorldDocument document = new WorldDocument(3, 3, 2);
        WorldObject first = new WorldObject(42, 10, 0, 0, 1, 1);
        WorldObject duplicate = new WorldObject(42, 10, 0, 0, 1, 1);
        WorldObject second = new WorldObject(43, 10, 1, 1, 1, 1);

        document.tile(0, 1, 1).restore(new TileSnapshot(
                10, 20, 30, 40,
                7, 0, 0, 0, 0,
                List.of(first, duplicate)));
        document.tile(1, 1, 1).restore(new TileSnapshot(
                50, 60, 70, 80,
                8, 0, 0, 0, 0,
                List.of(second)));

        WorldFragment fragment =
                WorldFragment.capture(document, new TileBounds(1, 1, 1, 1));

        assertEquals(2, fragment.terrain().size());
        assertEquals(0, fragment.terrain().get(0).plane());
        assertEquals(1, fragment.terrain().get(1).plane());
        assertTrue(fragment.terrain().get(0).snapshot().objects().isEmpty());
        assertTrue(fragment.terrain().get(1).snapshot().objects().isEmpty());
        assertEquals(List.of(first, second), fragment.objects());
    }

    @Test
    void capturePreservesExplicitNullFailuresAndBoundsValidation() {
        WorldDocument document = new WorldDocument(2, 2, 1);
        TileBounds bounds = new TileBounds(0, 0, 0, 0);

        NullPointerException documentFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragment.capture(null, bounds));
        assertEquals("document", documentFailure.getMessage());

        NullPointerException boundsFailure = assertThrows(
                NullPointerException.class,
                () -> WorldFragment.capture(document, null));
        assertEquals("bounds", boundsFailure.getMessage());

        IllegalArgumentException sizeFailure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragment.capture(
                        document,
                        new TileBounds(0, 0, 2, 1)));
        assertEquals(
                "Fragment bounds exceed document dimensions",
                sizeFailure.getMessage());
    }
}
