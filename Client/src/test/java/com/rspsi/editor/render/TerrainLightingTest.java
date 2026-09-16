package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TerrainLightingTest {
    @Test
    void flatInteriorUsesOsrsDirectionalLightAndEdgesRemainAmbient() {
        WorldDocument document = new WorldDocument(3, 3, 1);
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                document.tile(0, x, y).restore(new TileSnapshot(
                        0, 0, 0, 0, 1, 0, 0, 0, 0, List.of()));
            }
        }

        var lighting = TerrainLighting.build(document);

        assertEquals(84, lighting.get(new TileCoordinate(0, 1, 1)).southWest());
        assertEquals(96, lighting.get(new TileCoordinate(0, 0, 0)).southWest());
    }
}
