package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallDecorationPresentationTest {
    @Test
    void orientationZeroSwapsPrimaryAndSecondaryOrderAcrossTheDiagonal() {
        WorldTileAddress tile = WorldTileAddress.of(3200, 3200, 0);
        float centerX = tile.worldX() * 128.0f + 64.0f;
        float centerZ = tile.worldY() * 128.0f + 64.0f;
        WallDecorationPresentation primary =
                WallDecorationPresentation.primary(8, -8, 0);
        WallDecorationPresentation secondary =
                WallDecorationPresentation.secondary(0);

        CameraState west = new CameraState(centerX - 100.0f, -1000.0f, centerZ, 0, 0);
        CameraState east = new CameraState(centerX + 100.0f, -1000.0f, centerZ, 0, 0);

        assertEquals(0, primary.cameraOrder(tile, west));
        assertEquals(1, secondary.cameraOrder(tile, west));
        assertEquals(1, primary.cameraOrder(tile, east));
        assertEquals(0, secondary.cameraOrder(tile, east));
    }

    @Test
    void orientationTransformsMatchClientOrientation256Rule() {
        WorldTileAddress tile = WorldTileAddress.of(100, 100, 0);
        float centerX = tile.worldX() * 128.0f + 64.0f;
        float centerZ = tile.worldY() * 128.0f + 64.0f;
        CameraState camera = new CameraState(centerX - 100.0f, -1000.0f,
                centerZ - 25.0f, 0, 0);

        assertEquals(0, WallDecorationPresentation.primary(0, 0, 0)
                .cameraOrder(tile, camera));
        assertEquals(1, WallDecorationPresentation.primary(0, 0, 1)
                .cameraOrder(tile, camera));
        assertEquals(1, WallDecorationPresentation.primary(0, 0, 2)
                .cameraOrder(tile, camera));
        assertEquals(0, WallDecorationPresentation.primary(0, 0, 3)
                .cameraOrder(tile, camera));
    }
}
