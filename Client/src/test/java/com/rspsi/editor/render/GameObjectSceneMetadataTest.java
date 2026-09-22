package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void modelPacketAnchorRebaseMovesSceneBoundsWithTheObject() {
        ModelRenderPacket packet = new ModelRenderPacket(
                new TileCoordinate(0, 1, 1), 42, ObjectCategory.GROUND,
                List.of(), List.of(), List.of(), -1,
                0, 0, 0, 0, 0, 0, false, false)
                .withGameObjectSceneMetadata(
                        GameObjectSceneMetadata.of(1, 1, 2, 3, 0, 0));

        ModelRenderPacket world = packet.withAnchor(new TileCoordinate(0, 3200, 6400));

        assertEquals(3200, world.gameObjectSceneMetadata().minTileX());
        assertEquals(6400, world.gameObjectSceneMetadata().minTileY());
        assertEquals(3201, world.gameObjectSceneMetadata().maxTileX());
        assertEquals(6402, world.gameObjectSceneMetadata().maxTileY());
        assertEquals(2, world.gameObjectSceneMetadata().sizeX());
        assertEquals(3, world.gameObjectSceneMetadata().sizeY());
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
