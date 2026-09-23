package com.rspsi.editor.render;

import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceTemplateGridTest {
    @Test
    void exposesRuneLiteFourByThirteenByThirteenPackedShape() {
        InstanceChunkTemplate template = new InstanceChunkTemplate(
                2, 4, 7,
                1, 321, 654, 3);
        SceneWindow window = new SceneWindow(
                new WorldRegionWindow(10, 20, 1, 1, Map.of()),
                640, 1280, 4, 0, 0, -1,
                Set.of(), List.of(template));

        InstanceTemplateGrid grid = window.instanceTemplateGrid();
        int[][][] packed = grid.toPackedArray();

        assertEquals(4, packed.length);
        assertEquals(13, packed[0].length);
        assertEquals(13, packed[0][0].length);
        assertEquals(template.encode(), packed[2][4][7]);
        assertEquals(InstanceChunkTemplate.ABSENT, packed[0][0][0]);
        assertEquals(template, grid.templateAt(2, 4, 7).orElseThrow());
        assertTrue(grid.templateAt(0, 0, 0).isEmpty());
    }

    @Test
    void preservesCurrentOsrsTemplateBitLayoutExactly() {
        InstanceChunkTemplate template = new InstanceChunkTemplate(
                3, 12, 11,
                2, 0x2ab, 0x5cd, 2);

        int packed = template.encode();
        InstanceChunkTemplate decoded = InstanceChunkTemplate.decode(
                packed, template.targetPlane(), template.sceneChunkX(), template.sceneChunkY())
                .orElseThrow();

        assertEquals(template, decoded);
        assertEquals(2, (packed >>> 1) & 0x3);
        assertEquals(0x5cd, (packed >>> 3) & 0x7ff);
        assertEquals(0x2ab, (packed >>> 14) & 0x3ff);
        assertEquals(2, (packed >>> 24) & 0x3);
    }

    @Test
    void rejectsDuplicateOrOutOfSceneTargetSlots() {
        InstanceChunkTemplate first = new InstanceChunkTemplate(
                0, 1, 2, 0, 100, 200, 0);
        InstanceChunkTemplate duplicate = new InstanceChunkTemplate(
                0, 1, 2, 1, 101, 201, 1);
        assertThrows(IllegalArgumentException.class,
                () -> InstanceTemplateGrid.from(4, List.of(first, duplicate)));

        InstanceChunkTemplate outsideChunks = new InstanceChunkTemplate(
                0, 13, 0, 0, 100, 200, 0);
        assertThrows(IllegalArgumentException.class,
                () -> InstanceTemplateGrid.from(4, List.of(outsideChunks)));

        InstanceChunkTemplate outsidePlane = new InstanceChunkTemplate(
                4, 0, 0, 0, 100, 200, 0);
        assertThrows(IllegalArgumentException.class,
                () -> InstanceTemplateGrid.from(4, List.of(outsidePlane)));
    }

    @Test
    void rejectsPackedSourceValuesThatWouldTruncate() {
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 4, 10, 20, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 0, 0x400, 20, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new InstanceChunkTemplate(0, 0, 0, 0, 10, 0x800, 0));
    }

    @Test
    void defensiveArrayCannotMutateTheSceneApiView() {
        InstanceChunkTemplate template = new InstanceChunkTemplate(
                0, 0, 0, 0, 10, 20, 1);
        InstanceTemplateGrid grid = InstanceTemplateGrid.from(4, List.of(template));

        int[][][] copy = grid.toPackedArray();
        copy[0][0][0] = InstanceChunkTemplate.ABSENT;

        assertEquals(template.encode(), grid.packedAt(0, 0, 0));
    }
}
