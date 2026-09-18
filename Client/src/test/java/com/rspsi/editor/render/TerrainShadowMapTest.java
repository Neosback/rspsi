package com.rspsi.editor.render;

import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainShadowMapTest {
    @Test
    void wallOrientationWritesClientCornerShadowPositions() {
        TerrainShadowMap shadows = new TerrainShadowMap(1, 4, 4);
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, -1, 0,
                false, false, false, 0);

        shadows.addWallShadow(new WorldObject(1, 0, 0, 0, 1, 1), appearance);

        assertEquals(50, shadows.cornerStrength(0, 1, 1));
        assertEquals(50, shadows.cornerStrength(0, 1, 2));
        assertEquals(0, shadows.cornerStrength(0, 2, 1));
    }

    @Test
    void lightingAppliesTheClientWeightedCornerPenalty() {
        TerrainShadowMap shadows = new TerrainShadowMap(1, 4, 4);
        shadows.addCornerShadow(0, 2, 2, 50);

        var withoutShadow = TerrainLighting.build(
                new com.rspsi.editor.model.WorldDocument(4, 4, 1), LightingProfile.osrs());
        var withShadow = TerrainLighting.build(
                new com.rspsi.editor.model.WorldDocument(4, 4, 1), LightingProfile.osrs(), shadows);

        assertEquals(withoutShadow.get(new com.rspsi.editor.model.TileCoordinate(0, 2, 2)).southWest() - 25,
                withShadow.get(new com.rspsi.editor.model.TileCoordinate(0, 2, 2)).southWest());
    }

    @Test
    void clippedStaticObjectsContributeModelRadiusLightOcclusion() {
        TerrainShadowMap shadows = new TerrainShadowMap(1, 4, 4);
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, -1, 0,
                true, false, false, 0);
        ObjectDefinitionView definition = new ObjectDefinitionView(1, "test", 1, 2,
                java.util.List.of(), new int[0], new int[0], -1, false);

        shadows.addClippedObjectOcclusion(new WorldObject(1, 10, 1, 0, 1, 1),
                appearance, definition, new DefinitionProvider() {
                    @Override public java.util.Optional<ObjectDefinitionView> object(int id) {
                        return java.util.Optional.of(definition);
                    }
                    @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                        return java.util.Optional.empty();
                    }
                    @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                        return java.util.Optional.empty();
                    }
                });

        // Rotation swaps the 1x2 footprint, and the client includes both
        // footprint edges in its terrain-light occlusion grid.
        assertEquals(15, shadows.cornerStrength(0, 1, 1));
        assertEquals(15, shadows.cornerStrength(0, 3, 2));
        assertEquals(0, shadows.cornerStrength(0, 1, 3));
    }

    @Test
    void geometryBackedOcclusionUsesRadiusInsteadOfFallbackStrength() {
        TerrainShadowMap shadows = new TerrainShadowMap(1, 4, 4);
        ObjectAppearanceView appearance = new ObjectAppearanceView(
                -1, false, 128, 128, 128, 0, 0, 0, Map.of(), Map.of(),
                true, false, false, false, 0, 0, 16, -1, 0,
                true, false, false, 0);
        ObjectDefinitionView definition = new ObjectDefinitionView(1, "test", 1, 1,
                java.util.List.of(), new int[]{7}, new int[]{10}, -1, false);
        ModelGeometryView geometry = new ModelGeometryView(7,
                new int[]{8, 0, 0, 0, 0, 8, -8, 0, 0},
                new int[]{0, 1, 2}, new short[]{100}, new int[]{0}, new int[]{-1});
        DefinitionProvider provider = new DefinitionProvider() {
            @Override public java.util.Optional<ObjectDefinitionView> object(int id) {
                return java.util.Optional.of(definition);
            }
            @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> underlay(int id) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<com.rspsi.cache.definition.FloorDefinitionView> overlay(int id) {
                return java.util.Optional.empty();
            }
            @Override public java.util.Optional<ModelGeometryView> modelGeometry(int id) {
                return java.util.Optional.of(geometry);
            }
        };

        shadows.addClippedObjectOcclusion(new WorldObject(1, 10, 0, 0, 1, 1),
                appearance, definition, provider);

        // XZ radius is 8, so the client contribution is 8 / 4 = 2, not the
        // no-model fallback of 15.
        assertEquals(2, shadows.cornerStrength(0, 1, 1));
    }
}
