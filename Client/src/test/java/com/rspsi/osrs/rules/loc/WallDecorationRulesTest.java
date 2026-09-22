package com.rspsi.osrs.rules.loc;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.FloorDefinitionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallDecorationRulesTest {
    @Test
    void usesClientFallbackDisplacementWhenNoSupportingWallExists() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject decoration = new WorldObject(42, 5, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(decoration)));

        int displacement = WallDecorationRules.resolveDisplacement(
                decoration, appearance(64), document, definitions(Map.of()));

        assertEquals(WallDecorationRules.DEFAULT_FALLBACK_DISPLACEMENT, displacement);
        assertEquals(16, displacement);
    }

    @Test
    void inheritsDisplacementFromSupportingWallDefinition() {
        WorldDocument document = new WorldDocument(1, 1, 1);
        WorldObject wall = new WorldObject(100, 0, 0, 0, 0, 0);
        WorldObject decoration = new WorldObject(42, 6, 0, 0, 0, 0);
        document.tile(0, 0, 0).restore(new TileSnapshot(
                0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(wall, decoration)));

        int displacement = WallDecorationRules.resolveDisplacement(
                decoration, ObjectAppearanceView.empty(), document,
                definitions(Map.of(100, appearance(32))));

        assertEquals(32, displacement);
        assertEquals(16, WallDecorationRules.diagonalOffsetX(0, displacement));
        assertEquals(-16, WallDecorationRules.diagonalOffsetZ(1, displacement));
    }

    @Test
    void straightAndDiagonalOffsetVectorsCoverAllFourRotations() {
        int displacement = 32;
        int[][] straight = {
                {32, 0}, {0, -32}, {-32, 0}, {0, 32}
        };
        int[][] diagonal = {
                {16, -16}, {-16, -16}, {-16, 16}, {16, 16}
        };

        for (int rotation = 0; rotation < 4; rotation++) {
            assertEquals(straight[rotation][0],
                    WallDecorationRules.straightOffsetX(rotation, displacement));
            assertEquals(straight[rotation][1],
                    WallDecorationRules.straightOffsetZ(rotation, displacement));
            assertEquals(diagonal[rotation][0],
                    WallDecorationRules.diagonalOffsetX(rotation, displacement));
            assertEquals(diagonal[rotation][1],
                    WallDecorationRules.diagonalOffsetZ(rotation, displacement));
        }
    }

    private static ObjectAppearanceView appearance(int displacement) {
        return new ObjectAppearanceView(-1, false, 128, 128, 128,
                0, 0, 0, Map.of(), Map.of(), true, false, false, false,
                0, 0, displacement, -1, 0, false, false, false, 0,
                true, false);
    }

    private static DefinitionProvider definitions(Map<Integer, ObjectAppearanceView> appearances) {
        return new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> underlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<FloorDefinitionView> overlay(int id) {
                return Optional.empty();
            }

            @Override public Optional<ObjectAppearanceView> objectAppearance(int id) {
                return Optional.ofNullable(appearances.get(id));
            }
        };
    }
}
