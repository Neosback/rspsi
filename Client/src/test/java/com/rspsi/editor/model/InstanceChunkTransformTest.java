package com.rspsi.editor.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstanceChunkTransformTest {
    @Test
    void packedTemplateRoundTripsCurrentOsrsBitLayout() {
        InstanceChunkTemplate template = new InstanceChunkTemplate(2, 4, 5, 3, 401, 777, 3);

        assertEquals(template, InstanceChunkTemplate.decode(template.encode(),
                template.targetPlane(), template.sceneChunkX(), template.sceneChunkY()).orElseThrow());
        assertTrue(InstanceChunkTemplate.decode(-1, 0, 0, 0).isEmpty());
    }

    @Test
    void mapsEveryTileThroughAllFourRotationsAndBack() {
        for (int rotation = 0; rotation < 4; rotation++) {
            InstanceChunkTemplate template = new InstanceChunkTemplate(
                    1, 2, 3, 0, 400, 500, rotation);
            InstanceChunkTransform transform = new InstanceChunkTransform(template, 3200, 3216);
            for (int localX = 0; localX < 8; localX++) {
                for (int localY = 0; localY < 8; localY++) {
                    TileCoordinate source = new TileCoordinate(0, 400 * 8 + localX, 500 * 8 + localY);
                    TileCoordinate scene = transform.sourceToScene(source);
                    assertTrue(transform.containsScene(scene), rotation + ":" + scene);
                    assertEquals(source, transform.sceneToSource(scene), rotation + ":" + source);
                }
            }
        }
    }

    @Test
    void rotationMatchesRuneLiteChunkCoordinateConvention() {
        InstanceChunkTransform transform = new InstanceChunkTransform(
                new InstanceChunkTemplate(0, 1, 2, 0, 10, 20, 1), 3200, 3216);

        assertEquals(new TileCoordinate(0, 3208, 3239),
                transform.sourceToScene(new TileCoordinate(0, 80, 160)));
        assertEquals(new TileCoordinate(0, 3208, 3238),
                transform.sourceToScene(new TileCoordinate(0, 81, 160)));
        assertEquals(1, transform.sourceObjectRotationToScene(0));
    }

    @Test
    void mapsObjectAnchorPlaneAndOrientation() {
        InstanceChunkTransform transform = new InstanceChunkTransform(
                new InstanceChunkTemplate(2, 0, 0, 1, 30, 40, 2), 0, 0);
        WorldObject mapped = transform.sourceObjectToScene(new WorldObject(42, 10, 1, 1, 241, 322));

        assertEquals(new WorldObject(42, 10, 3, 2, 6, 5), mapped);
    }
}
