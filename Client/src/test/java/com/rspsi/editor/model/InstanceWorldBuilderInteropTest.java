package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InstanceWorldBuilderInteropTest {

    @Test
    void remainsPublicFinalWithPublicNoArgConstructorAndOverloads() throws Exception {
        assertTrue(Modifier.isPublic(InstanceWorldBuilder.class.getModifiers()));
        assertTrue(Modifier.isFinal(InstanceWorldBuilder.class.getModifiers()));
        assertTrue(Modifier.isPublic(
                InstanceWorldBuilder.class.getConstructor().getModifiers()));

        assertNotNull(InstanceWorldBuilder.class.getMethod(
                "build",
                WorldRegionWindow.class,
                InstanceChunkGrid.class,
                int.class,
                int.class,
                int.class));
        assertNotNull(InstanceWorldBuilder.class.getMethod(
                "build",
                WorldRegionWindow.class,
                InstanceChunkGrid.class,
                int.class,
                int.class,
                int.class,
                InstanceObjectFootprintResolver.class));
        assertNotNull(InstanceWorldBuilder.class.getMethod(
                "build",
                WorldRegionWindow.class,
                InstanceChunkGrid.class,
                int.class,
                int.class,
                int.class,
                InstanceObjectFootprintResolver.class,
                InstanceGeneratedHeightProvider.class));
    }

    @Test
    void preservesExplicitNullFailuresAndValidationOrder() {
        InstanceWorldBuilder builder = new InstanceWorldBuilder();
        WorldRegionWindow source = sourceWindow(new WorldDocument(64, 64, 1));
        InstanceChunkGrid grid = emptyGrid();

        NullPointerException sourceFailure = assertThrows(
                NullPointerException.class,
                () -> builder.build(null, grid, 8, 8, 1));
        assertEquals("source", sourceFailure.getMessage());

        NullPointerException gridFailure = assertThrows(
                NullPointerException.class,
                () -> builder.build(source, null, 8, 8, 1));
        assertEquals("grid", gridFailure.getMessage());

        NullPointerException resolverFailure = assertThrows(
                NullPointerException.class,
                () -> builder.build(
                        source, grid, 8, 8, 1, null,
                        InstanceGeneratedHeightProvider.required()));
        assertEquals("resolver", resolverFailure.getMessage());

        NullPointerException heightFailure = assertThrows(
                NullPointerException.class,
                () -> builder.build(
                        source, grid, 8, 8, 1,
                        InstanceObjectFootprintResolver.unit(), null));
        assertEquals("generatedHeightProvider", heightFailure.getMessage());

        IllegalArgumentException dimensions = assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(source, grid, 0, 8, 1));
        assertEquals(
                "Instance document dimensions must be positive",
                dimensions.getMessage());
    }

    @Test
    void rejectsTargetPlaneOutsideDestination() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 1);
        WorldRegionWindow source = sourceWindow(sourceDocument);
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(1, 0, 0, 0, 0, 0, 0);
        InstanceChunkGrid grid =
                InstanceChunkGrid.decode(
                        new int[][][]{{{-1}}, {{template.encode()}}},
                        0,
                        0);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new InstanceWorldBuilder().build(source, grid, 8, 8, 1));

        assertEquals(
                "Instance target plane is outside destination: 1",
                failure.getMessage());
    }

    @Test
    void preservesGeneratedHeightReplayAndProvenance() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 1);
        Tile sourceTile = sourceDocument.tile(0, 1, 1);
        sourceTile.restore(new TileSnapshot(
                10, 20, 30, 40,
                1, 2, 3, 0, 0,
                List.of()));
        sourceTile.heightSource(TerrainHeightSource.generatedSource());

        WorldRegionWindow source = sourceWindow(sourceDocument);
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 0);
        InstanceChunkGrid grid =
                InstanceChunkGrid.decode(
                        new int[][][]{{{template.encode()}}},
                        0,
                        0);

        WorldDocument result = new InstanceWorldBuilder().build(
                source,
                grid,
                8,
                8,
                1,
                InstanceObjectFootprintResolver.unit(),
                (x, y) -> {
                    assertEquals(1, x);
                    assertEquals(1, y);
                    return -1234;
                });

        Tile copied = result.tile(0, 1, 1);
        assertEquals(-1234, copied.snapshot().southWestHeight());
        assertEquals(TerrainHeightSource.generatedSource(), copied.heightSource());
    }

    @Test
    void preservesNullFootprintFailureForPlacedObject() {
        WorldDocument sourceDocument = new WorldDocument(64, 64, 1);
        sourceDocument.tile(0, 1, 1).restore(new TileSnapshot(
                0, 0, 0, 0,
                0, 0, 0, 0, 0,
                List.of(new WorldObject(42, 10, 0, 0, 1, 1))));

        WorldRegionWindow source = sourceWindow(sourceDocument);
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 0);
        InstanceChunkGrid grid =
                InstanceChunkGrid.decode(
                        new int[][][]{{{template.encode()}}},
                        0,
                        0);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new InstanceWorldBuilder().build(
                        source,
                        grid,
                        8,
                        8,
                        1,
                        object -> null,
                        InstanceGeneratedHeightProvider.required()));

        assertEquals(
                "Footprint resolver returned null for object 42",
                failure.getMessage());
    }

    private static WorldRegionWindow sourceWindow(WorldDocument document) {
        WorldRegion region = new WorldRegion(0, 0, document);
        return new WorldRegionWindow(
                0,
                0,
                1,
                1,
                Map.of(region.regionId(), region));
    }

    private static InstanceChunkGrid emptyGrid() {
        return InstanceChunkGrid.decode(new int[][][]{{{-1}}}, 0, 0);
    }
}
