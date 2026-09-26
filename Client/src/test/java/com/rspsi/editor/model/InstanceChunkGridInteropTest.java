package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("deprecation")
class InstanceChunkGridInteropTest {

    @Test
    void decodeRemainsTrueJavaStaticFactory() throws Exception {
        assertTrue(Modifier.isStatic(
                InstanceChunkGrid.class
                        .getMethod("decode", int[][][].class, int.class, int.class)
                        .getModifiers()));
    }

    @Test
    void preservesHolesDuplicatesAndImmutableTransforms() {
        InstanceChunkTemplate first =
                new InstanceChunkTemplate(0, 0, 0, 0, 100, 200, 0);
        InstanceChunkTemplate second =
                new InstanceChunkTemplate(0, 1, 0, 0, 100, 200, 1);

        InstanceChunkGrid grid =
                InstanceChunkGrid.decode(
                        new int[][][]{{{first.encode()}, {second.encode()}, {-1}}},
                        3200,
                        3216);

        assertEquals(3200, grid.sceneBaseX());
        assertEquals(3216, grid.sceneBaseY());
        assertEquals(2, grid.transforms().size());
        assertThrows(UnsupportedOperationException.class,
                () -> grid.transforms().add(grid.transforms().get(0)));

        TileCoordinate source = new TileCoordinate(0, 800, 1600);
        assertEquals(
                List.of(
                        new TileCoordinate(0, 3200, 3216),
                        new TileCoordinate(0, 3208, 3223)),
                grid.sourceToScene(source));
        assertTrue(grid.sceneToSource(new TileCoordinate(0, 3216, 3216)).isEmpty());
    }

    @Test
    void mappingResultsRemainUnmodifiableAndNullChecked() {
        InstanceChunkTemplate template =
                new InstanceChunkTemplate(0, 0, 0, 0, 0, 0, 0);
        InstanceChunkGrid grid =
                InstanceChunkGrid.decode(new int[][][]{{{template.encode()}}}, 0, 0);

        List<TileCoordinate> mapped =
                grid.sourceToScene(new TileCoordinate(0, 0, 0));
        assertThrows(UnsupportedOperationException.class,
                () -> mapped.add(new TileCoordinate(0, 1, 1)));

        NullPointerException sourceFailure = assertThrows(
                NullPointerException.class,
                () -> grid.sourceToScene(null));
        assertEquals("source", sourceFailure.getMessage());

        NullPointerException sceneFailure = assertThrows(
                NullPointerException.class,
                () -> grid.sceneToSource(null));
        assertEquals("scene", sceneFailure.getMessage());
    }

    @Test
    void decodePreservesValidationAndNestedNullFailures() {
        assertThrows(IllegalArgumentException.class,
                () -> InstanceChunkGrid.decode(new int[][][]{{{-1}}}, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> InstanceChunkGrid.decode(new int[][][]{{{-1}}}, 0, -1));

        NullPointerException top = assertThrows(
                NullPointerException.class,
                () -> InstanceChunkGrid.decode(null, 0, 0));
        assertEquals("packedTemplates", top.getMessage());

        int[][][] nullPlane = new int[1][][];
        NullPointerException plane = assertThrows(
                NullPointerException.class,
                () -> InstanceChunkGrid.decode(nullPlane, 0, 0));
        assertEquals("packedTemplates[plane]", plane.getMessage());

        int[][][] nullColumn = new int[1][1][];
        NullPointerException column = assertThrows(
                NullPointerException.class,
                () -> InstanceChunkGrid.decode(nullColumn, 0, 0));
        assertEquals("packedTemplates[plane][x]", column.getMessage());
    }
}
