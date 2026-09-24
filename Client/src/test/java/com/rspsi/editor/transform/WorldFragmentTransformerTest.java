package com.rspsi.editor.transform;

import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.osrs.rules.tile.TileShapeRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentTransformerTest {

    @Test
    void rotatesRectangularMultiPlaneFragmentWithHeightsOverlaysAndObjects() {
        TileBounds bounds = new TileBounds(100, 200, 102, 203);
        TileSnapshot source = snapshot(11, 12, 13, 14, 7, 9, 2, 1);
        TerrainTilePatch plane0 = new TerrainTilePatch(0, 100, 200, source);
        TerrainTilePatch plane1 = new TerrainTilePatch(
                1, 102, 203, snapshot(21, 22, 23, 24, 3, 5, 4, 2));
        WorldObject booth = new WorldObject(42, 10, 0, 0, 100, 200);
        WorldFragment fragment = new WorldFragment(
                bounds, List.of(plane0, plane1), List.of(booth));

        WorldFragmentTransformResult result = WorldFragmentTransformer.transform(
                fragment,
                WorldFragmentTransform.rotate(1),
                resolver(Map.of(42, new ObjectFootprintResolver.ObjectFootprint(3, 2))));

        assertEquals(new TileBounds(100, 200, 103, 202), result.fragment().bounds());

        TerrainTilePatch transformed0 = result.fragment().terrain().stream()
                .filter(patch -> patch.plane() == 0).findFirst().orElseThrow();
        assertEquals(100, transformed0.x());
        assertEquals(202, transformed0.y());
        // Rotation 1 maps source SE->target SW, NE->SE, NW->NE, SW->NW.
        assertEquals(12, transformed0.snapshot().southWestHeight());
        assertEquals(13, transformed0.snapshot().southEastHeight());
        assertEquals(14, transformed0.snapshot().northEastHeight());
        assertEquals(11, transformed0.snapshot().northWestHeight());
        assertEquals(TerrainHeightSource.unknown(), transformed0.snapshot().heightSource());

        TileShapeRules.OverlayTransform expectedOverlay =
                TileShapeRules.rotateOverlay(2, 1, 1);
        assertEquals(expectedOverlay.shape(), transformed0.snapshot().overlayShape());
        assertEquals(expectedOverlay.rotation(), transformed0.snapshot().overlayRotation());

        TerrainTilePatch transformed1 = result.fragment().terrain().stream()
                .filter(patch -> patch.plane() == 1).findFirst().orElseThrow();
        assertEquals(103, transformed1.x());
        assertEquals(200, transformed1.y());

        assertEquals(List.of(new WorldObject(42, 10, 1, 0, 100, 200)),
                result.fragment().objects());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void customPivotKeepsSelectedSourceTileAtSameWorldCoordinate() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(10, 20, 12, 21),
                List.of(
                        new TerrainTilePatch(0, 10, 20, snapshot(1, 2, 3, 4, 1, 1, 1, 0)),
                        new TerrainTilePatch(0, 12, 21, snapshot(5, 6, 7, 8, 2, 1, 1, 0))),
                List.of());

        WorldFragmentTransform transform = WorldFragmentTransform.rotate(1)
                .around(new WorldFragmentTransform.Pivot(12, 21));
        WorldFragmentTransformResult result = WorldFragmentTransformer.transform(
                fragment, transform, object -> Optional.empty());

        TerrainTilePatch pivotPatch = result.fragment().terrain().stream()
                .filter(patch -> patch.snapshot().underlayId() == 2)
                .findFirst().orElseThrow();
        assertEquals(12, pivotPatch.x());
        assertEquals(21, pivotPatch.y());
        assertEquals(new TileBounds(11, 19, 12, 21), result.fragment().bounds());
    }

    @Test
    void mirrorUsesBestRepresentableObjectOrientationWithoutChangingFootprint() {
        WorldObject diagonal = new WorldObject(77, 1, 0, 0, 20, 30);
        WorldFragment fragment = new WorldFragment(
                new TileBounds(20, 30, 22, 31),
                List.of(),
                List.of(diagonal));

        WorldFragmentTransformResult result = WorldFragmentTransformer.transform(
                fragment,
                WorldFragmentTransform.mirrorX(),
                resolver(Map.of(77, new ObjectFootprintResolver.ObjectFootprint(2, 1))));

        WorldObject transformed = result.fragment().objects().get(0);
        assertEquals(21, transformed.x());
        assertEquals(30, transformed.y());
        assertEquals(0, transformed.rotation(),
                "non-square footprint must not be silently swapped by mirror orientation");
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code()
                        == WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE));
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code()
                        == WorldFragmentTransformResult.DiagnosticCode.OBJECT_ORIENTATION_APPROXIMATED));
    }

    @Test
    void rejectsPartiallyCapturedMultiTileObject() {
        WorldObject object = new WorldObject(90, 10, 0, 0, 31, 40);
        WorldFragment fragment = new WorldFragment(
                new TileBounds(30, 40, 32, 42),
                List.of(),
                List.of(object));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragmentTransformer.transform(
                        fragment,
                        WorldFragmentTransform.rotate(1),
                        resolver(Map.of(90,
                                new ObjectFootprintResolver.ObjectFootprint(3, 2)))));
        assertTrue(failure.getMessage().contains("extends outside fragment bounds"));
    }

    @Test
    void rejectsObjectsWhenDefinitionFootprintIsUnavailable() {
        WorldFragment fragment = new WorldFragment(
                new TileBounds(10, 10, 10, 10),
                List.of(),
                List.of(new WorldObject(99, 10, 0, 0, 10, 10)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> WorldFragmentTransformer.transform(
                        fragment, WorldFragmentTransform.identity(),
                        object -> Optional.empty()));
        assertTrue(failure.getMessage().contains("Missing object footprint"));
    }

    @Test
    void rejectsEmbeddedObjectsInsideTerrainSnapshots() {
        WorldObject embedded = new WorldObject(5, 10, 0, 0, 10, 10);
        TileSnapshot snapshot = new TileSnapshot(
                0, 0, 0, 0, 1, 0, 0, 0, 0, List.of(embedded));
        WorldFragment fragment = new WorldFragment(
                new TileBounds(10, 10, 10, 10),
                List.of(new TerrainTilePatch(0, 10, 10, snapshot)),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> WorldFragmentTransformer.transform(
                        fragment, WorldFragmentTransform.identity(),
                        object -> Optional.empty()));
    }

    private static TileSnapshot snapshot(
            int sw, int se, int ne, int nw,
            int underlay, int overlay, int shape, int rotation
    ) {
        return new TileSnapshot(
                sw, se, ne, nw,
                underlay, overlay, shape, rotation, 3, List.of());
    }

    private static ObjectFootprintResolver resolver(
            Map<Integer, ObjectFootprintResolver.ObjectFootprint> footprints
    ) {
        return object -> Optional.ofNullable(footprints.get(object.id()));
    }
}
