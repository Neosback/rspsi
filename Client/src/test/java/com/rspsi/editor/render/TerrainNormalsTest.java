package com.rspsi.editor.render;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainNormalsTest {
    @Test
    void flatInteriorUsesClientScaleUpNormal() {
        WorldDocument document = slopedDocument(4, 4, 0, 0);

        TerrainNormalTile normals = TerrainNormals.buildTile(
                document, LightingProfile.osrs(), 0, 1, 1);

        assertEquals(TerrainNormal.FLAT, normals.southWest());
    }

    @Test
    void slopeNormalUsesSameHeightDerivativesAsVanillaLighting() {
        WorldDocument document = slopedDocument(4, 4, 20, 10);
        LightingProfile profile = LightingProfile.osrs();

        TerrainNormalTile normals = TerrainNormals.buildTile(document, profile, 0, 1, 1);
        TerrainNormal expected =
                com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateCornerNormal(
                        40, 20, profile.heightScale());

        assertEquals(expected, normals.southWest());
    }

    @Test
    void adjacentTilesShareExactlyTheSameEdgeNormal() {
        WorldDocument document = slopedDocument(4, 4, 20, 10);
        LightingProfile profile = LightingProfile.osrs();

        TerrainNormalTile west = TerrainNormals.buildTile(document, profile, 0, 1, 1);
        TerrainNormalTile east = TerrainNormals.buildTile(document, profile, 0, 2, 1);

        assertEquals(west.southEast(), east.southWest());
        assertEquals(west.northEast(), east.northWest());
    }

    private static WorldDocument slopedDocument(int width, int length, int xStep, int yStep) {
        WorldDocument document = new WorldDocument(width, length, 1);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                document.tile(0, x, y).restore(new TileSnapshot(
                        height(x, y, xStep, yStep),
                        height(x + 1, y, xStep, yStep),
                        height(x + 1, y + 1, xStep, yStep),
                        height(x, y + 1, xStep, yStep),
                        1, 0, 0, 0, 0, List.of()));
            }
        }
        return document;
    }

    private static int height(int x, int y, int xStep, int yStep) {
        return x * xStep + y * yStep;
    }
}
