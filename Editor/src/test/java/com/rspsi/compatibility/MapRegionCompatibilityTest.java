package com.rspsi.compatibility;

import com.jagex.map.MapRegion;
import com.jagex.map.SceneGraph;
import com.jagex.chunk.Chunk;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class MapRegionCompatibilityTest {
    @Test
    void terrainFixtureHasExpectedLegacySize() throws IOException {
        byte[] fixture = readResource("/misc/blank_region.dat");
        MapRegion region = new MapRegion(null, 64, 64);
        region.unpackTiles(fixture, 0, 0, 0, 0);

        assertEquals(32768, fixture.length);
        assertEquals(4, region.tileHeights.length);
        assertEquals(65, region.tileHeights[0].length);
        assertEquals(region.tileHeights[0][0][0], region.tileHeights[0][64][64]);
    }

    @Test
    void blankObjectFixtureLoadsAsAnEmptyLegacyScene() throws IOException {
        byte[] fixture = readResource("/misc/blank_regionO.dat");
        assertEquals(1, fixture.length);

        MapRegion region = new MapRegion(null, 64, 64);
        SceneGraph scene = new SceneGraph(64, 64, 4);
        region.unpackObjects(scene, fixture, 0, 0);

        for (int plane = 0; plane < scene.tiles.length; plane++) {
            for (int x = 0; x < scene.width; x++) {
                for (int y = 0; y < scene.length; y++) {
                    assertNull(scene.tiles[plane][x][y],
                            "blank object fixture materialized a tile at " + plane + "," + x + "," + y);
                }
            }
        }
    }

    @Test
    void heightAndLightingHelpersRemainDeterministic() {
        assertEquals(MapRegion.calculateHeight(100, 200), MapRegion.calculateHeight(100, 200));
        assertEquals(0xbc614e, MapRegion.light(-1, 64));
        assertEquals(MapRegion.light(0x1234, 64), MapRegion.light(0x1234, 64));
    }

    @Test
    void terrainEncodingPreservesEditableTileState() {
        for (int shape = 0; shape <= 11; shape++) {
            for (int rotation = 0; rotation <= 3; rotation++) {
                MapRegion original = new MapRegion(null, 64, 64);
                original.tileHeights[0][2][3] = -80;
                original.manualTileHeight[0][2][3] = 1;
                original.underlays[0][2][3] = 7;
                original.overlays[0][2][3] = 9;
                original.overlayShapes[0][2][3] = (byte) shape;
                original.overlayOrientations[0][2][3] = (byte) rotation;
                original.tileFlags[0][2][3] = 8;

                Chunk chunk = new Chunk(0);
                byte[] encoded = original.saveTerrainBlock(chunk);
                assertArrayEquals(encoded, original.save_terrain_block(chunk));
                MapRegion decoded = new MapRegion(null, 64, 64);
                decoded.unpackTiles(encoded, 0, 0, 0, 0);

                assertEquals(-80, decoded.tileHeights[0][2][3]);
                assertEquals(7, decoded.underlays[0][2][3]);
                assertEquals(9, decoded.overlays[0][2][3]);
                assertEquals(shape, decoded.overlayShapes[0][2][3]);
                assertEquals(rotation, decoded.overlayOrientations[0][2][3]);
                assertEquals(8, decoded.tileFlags[0][2][3]);
            }
        }
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream stream = MapRegionCompatibilityTest.class.getResourceAsStream(name)) {
            assertNotNull(stream, "Missing fixture " + name);
            return stream.readAllBytes();
        }
    }
}
