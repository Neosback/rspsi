package com.rspsi.osrs.rules.tile;

import com.rspsi.editor.terrain.autotile.OverlayShapeAtlas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TileShapeTransformRulesTest {

    @Test
    void overlayTransformsMatchSceneTileModelGeometryForEveryNativeShape() {
        for (int shape = 0; shape < TileShapeRules.OVERLAY_SHAPE_COUNT; shape++) {
            for (int rotation = 0; rotation < 4; rotation++) {
                for (boolean mirrorX : new boolean[]{false, true}) {
                    for (boolean mirrorY : new boolean[]{false, true}) {
                        for (int turns = 0; turns < 4; turns++) {
                            TileShapeRules.OverlayTransform transformed =
                                    TileShapeRules.transformOverlay(
                                            shape, rotation, mirrorX, mirrorY, turns);

                            OverlayShapeAtlas.Entry source =
                                    OverlayShapeAtlas.entry(shape, rotation);
                            OverlayShapeAtlas.Entry target =
                                    OverlayShapeAtlas.entry(
                                            transformed.shape(), transformed.rotation());

                            for (int ix = 0; ix < 19; ix++) {
                                for (int iy = 0; iy < 17; iy++) {
                                    // Deliberately asymmetric interior samples avoid
                                    // native quarter/diagonal boundaries.
                                    double x = (ix + 0.371) / 19.0;
                                    double y = (iy + 0.613) / 17.0;
                                    double[] sourcePoint =
                                            inverseTransform(x, y, mirrorX, mirrorY, turns);
                                    assertEquals(
                                            source.covers(sourcePoint[0], sourcePoint[1]),
                                            target.covers(x, y),
                                            "shape=" + shape + " rotation=" + rotation
                                                    + " mx=" + mirrorX + " my=" + mirrorY
                                                    + " turns=" + turns + " @" + x + "," + y);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static double[] inverseTransform(
            double x, double y,
            boolean mirrorX, boolean mirrorY,
            int quarterTurns
    ) {
        double sourceX;
        double sourceY;
        switch (quarterTurns & 3) {
            case 0 -> {
                sourceX = x;
                sourceY = y;
            }
            case 1 -> {
                sourceX = 1.0 - y;
                sourceY = x;
            }
            case 2 -> {
                sourceX = 1.0 - x;
                sourceY = 1.0 - y;
            }
            case 3 -> {
                sourceX = y;
                sourceY = 1.0 - x;
            }
            default -> throw new IllegalStateException();
        }
        if (mirrorY) sourceY = 1.0 - sourceY;
        if (mirrorX) sourceX = 1.0 - sourceX;
        return new double[]{sourceX, sourceY};
    }
}
