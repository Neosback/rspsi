package com.rspsi.osrs.rules.loc;

import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallRulesTest {
    private static final int DISPLACEMENT = 32;
    private static final int[][] STRAIGHT = {
            {32, 0}, {0, -32}, {-32, 0}, {0, 32}
    };
    private static final int[][] DIAGONAL = {
            {16, -16}, {-16, -16}, {-16, 16}, {16, 16}
    };

    @Test
    void wallShapesZeroThroughThreePreserveClientRotations() {
        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(List.of(variant(0, rotation, 0, 0, false)),
                    expand(0, rotation));
            assertEquals(List.of(variant(1, rotation, 0, 0, false)),
                    expand(1, rotation));
            assertEquals(List.of(
                            variant(2, rotation + 4, 0, 0, false),
                            variant(2, (rotation + 1) & 3, 0, 0, false)),
                    expand(2, rotation));
            assertEquals(List.of(variant(3, rotation, 0, 0, false)),
                    expand(3, rotation));
        }
    }

    @Test
    void wallDecorationsFourThroughEightMatchClientVariantMatrix() {
        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(List.of(variant(4, rotation, 0, 0, false)),
                    expand(4, rotation));

            assertEquals(List.of(variant(4, rotation,
                            STRAIGHT[rotation][0], STRAIGHT[rotation][1], false)),
                    expand(5, rotation));

            assertEquals(List.of(variant(4, rotation + 4,
                            DIAGONAL[rotation][0], DIAGONAL[rotation][1], false)),
                    expand(6, rotation));

            assertEquals(List.of(variant(4, ((rotation + 2) & 3) + 4,
                            0, 0, false)),
                    expand(7, rotation));

            assertEquals(List.of(
                            variant(4, rotation + 4,
                                    DIAGONAL[rotation][0], DIAGONAL[rotation][1], false),
                            variant(4, ((rotation + 2) & 3) + 4,
                                    0, 0, false)),
                    expand(8, rotation));
        }
    }

    @Test
    void diagonalGameObjectUsesTypeTenAndPostScaleAngleRotation() {
        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(List.of(variant(10, rotation + 4, 0, 0, true)),
                    expand(11, rotation));
        }
    }

    @Test
    void nonWallShapesRemainSingleUnmodifiedVariants() {
        for (int type : new int[]{9, 10, 12, 22}) {
            for (int rotation = 0; rotation < 4; rotation++) {
                assertEquals(List.of(variant(type, rotation, 0, 0, false)),
                        expand(type, rotation));
            }
        }
    }

    private static List<WallRules.LocModelVariant> expand(int type, int rotation) {
        return WallRules.expandVariants(
                new WorldObject(42, type, rotation, 0, 0, 0), DISPLACEMENT);
    }

    private static WallRules.LocModelVariant variant(int sourceType, int rotation,
                                                     int decorX, int decorZ,
                                                     boolean rotateAfterScale) {
        return new WallRules.LocModelVariant(
                sourceType, rotation, decorX, decorZ, rotateAfterScale);
    }
}
