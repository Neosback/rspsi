package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallDecorationPresentationTest {
    private static final WorldTileAddress TILE = WorldTileAddress.of(3200, 3200, 0);
    private static final float CENTER_X = 3200 * 128.0f + 64.0f;
    private static final float CENTER_Z = 3200 * 128.0f + 64.0f;

    @Test
    void shapeEightCameraOrderMatchesClientOrientation256Rule() {
        float[][] cameraOffsets = {
                {-256, 0},
                {256, 0},
                {0, -256},
                {0, 256},
                {-256, -256},
                {256, -256},
                {-256, 256},
                {256, 256}
        };

        for (int orientation = 0; orientation < 4; orientation++) {
            WallDecorationPresentation primary =
                    WallDecorationPresentation.primary(8, -8, orientation);
            WallDecorationPresentation secondary =
                    WallDecorationPresentation.secondary(orientation);

            for (float[] offset : cameraOffsets) {
                CameraState camera = new CameraState(
                        CENTER_X + offset[0], -1000.0f, CENTER_Z + offset[1],
                        0.0f, 0.0f);
                boolean primaryFirst = clientPrimaryFirst(orientation, camera);

                assertEquals(primaryFirst ? 0 : 1,
                        primary.cameraOrder(TILE, camera));
                assertEquals(primaryFirst ? 1 : 0,
                        secondary.cameraOrder(TILE, camera));
            }
        }
    }

    @Test
    void ordinaryObjectsHaveNoCameraDependentDecorationRank() {
        assertEquals(0, WallDecorationPresentation.none().cameraOrder(
                TILE, new CameraState(CENTER_X - 256, -1000.0f,
                        CENTER_Z, 0, 0)));
    }

    /**
     * RuneLite-melxin Scene.java orientation == 256:
     * transformed Z < transformed X submits renderable1 in the primary tile
     * phase; the later tile phase uses the complementary comparison.
     */
    private static boolean clientPrimaryFirst(int orientation, CameraState camera) {
        float dx = CENTER_X - camera.x();
        float dz = CENTER_Z - camera.z();
        float transformedX = orientation == 1 || orientation == 2 ? -dx : dx;
        float transformedZ = orientation == 2 || orientation == 3 ? -dz : dz;
        return transformedZ < transformedX;
    }
}
