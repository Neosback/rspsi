package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameObjectSceneMetadataTest {
    @Test
    void rebasesOccupiedTilesWithoutChangingSizeOrOrientation() {
        GameObjectSceneMetadata local =
                GameObjectSceneMetadata.of(5, 7, 3, 2, 1, 0);

        GameObjectSceneMetadata world = local.translated(3200, 6400);

        assertEquals(3205, world.minTileX());
        assertEquals(6407, world.minTileY());
        assertEquals(3207, world.maxTileX());
        assertEquals(6408, world.maxTileY());
        assertEquals(3, world.sizeX());
        assertEquals(2, world.sizeY());
        assertEquals(512, world.orientation());
        assertEquals(0, world.modelOrientation());
        assertEquals(1, world.rotation());
        assertTrue(world.contains(3206, 6408));
    }

    @Test
    void noneHasNoOccupiedSceneRectangle() {
        GameObjectSceneMetadata none = GameObjectSceneMetadata.none();

        assertTrue(!none.present());
        assertEquals(0, none.sizeX());
        assertEquals(0, none.sizeY());
        assertTrue(!none.contains(0, 0));
    }
}
