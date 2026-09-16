package com.rspsi.cache.map;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsrsRegionEncoderTest {
    @Test
    void decodeEncodeDecodePreservesTerrainAndLocationsSemantics() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int southWest = -16 * (x + y + plane * 15);
                    int eastX = x == 63 ? x : x + 1;
                    int northY = y == 63 ? y : y + 1;
                    int southEast = -16 * (eastX + y + plane * 15);
                    int northEast = -16 * (eastX + northY + plane * 15);
                    int northWest = -16 * (x + northY + plane * 15);
                    int overlay = plane == 0 && x == 4 && y == 5 ? 23 : 0;
                    int shape = overlay == 0 ? 0 : 11;
                    int rotation = overlay == 0 ? 0 : 1;
                    int underlay = plane == 1 && x == 2 && y == 3 ? 7 : 0;
                    int flags = plane == 2 && x == 7 && y == 8 ? 6 : 0;
                    List<WorldObject> objects = plane == 3 && x == 9 && y == 10
                            ? List.of(new WorldObject(100, 10, 2, 3, 9, 10))
                            : List.of();
                    source.tile(plane, x, y).restore(new TileSnapshot(
                            southWest, southEast, northEast, northWest,
                            underlay, overlay, shape, rotation, flags, objects));
                }
            }
        }

        byte[] terrain = OsrsRegionEncoder.encodeTerrain(source);
        byte[] locations = OsrsRegionEncoder.encodeLocations(source);
        WorldDocument decoded = OsrsRegionDecoder.decode(
                terrain, locations, 0, 0, (x, y) -> 10);

        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    assertEquals(source.tile(plane, x, y).snapshot(),
                            decoded.tile(plane, x, y).snapshot(),
                            "semantic mismatch at " + plane + "," + x + "," + y);
                }
            }
        }
    }

    @Test
    void decodeEncodeDecodePreservesLegacyByteTerrainSemantics() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int southWest = -16 * (x + y + plane * 4);
                    int eastX = x == 63 ? x : x + 1;
                    int northY = y == 63 ? y : y + 1;
                    source.tile(plane, x, y).restore(new TileSnapshot(
                            southWest,
                            -16 * (eastX + y + plane * 4),
                            -16 * (eastX + northY + plane * 4),
                            -16 * (x + northY + plane * 4),
                            plane == 1 && x == 2 && y == 3 ? 7 : 0,
                            plane == 0 && x == 4 && y == 5 ? 200 : 0,
                            plane == 0 && x == 4 && y == 5 ? 11 : 0,
                            plane == 0 && x == 4 && y == 5 ? 1 : 0,
                            plane == 2 && x == 7 && y == 8 ? 6 : 0,
                            List.of()));
                }
            }
        }

        byte[] terrain = OsrsRegionEncoder.encodeTerrain(source, false);
        WorldDocument decoded = OsrsRegionDecoder.decodeTerrain(terrain, 0, 0, (x, y) -> 10, false);

        for (int plane = 0; plane < 4; plane++) {
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    assertEquals(source.tile(plane, x, y).snapshot(),
                            decoded.tile(plane, x, y).snapshot(),
                            "legacy semantic mismatch at " + plane + "," + x + "," + y);
                }
            }
        }
    }

    @Test
    void rejectsCrackedSharedTerrainEdgesBeforeEncoding() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        source.tile(0, 0, 0).restore(new TileSnapshot(
                0, 8, 0, 0, 0, 0, 0, 0, 0, List.of()));

        assertThrows(IllegalArgumentException.class, () -> OsrsRegionEncoder.encodeTerrain(source));
    }

    @Test
    void rejectsLocationTypesOutsideTheCanonicalOsrsShapeTable() {
        WorldDocument source = new WorldDocument(64, 64, 4);
        source.tile(0, 0, 0).restore(new TileSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(new WorldObject(1, 23, 0, 0, 0, 0))));

        assertThrows(IllegalArgumentException.class, () -> OsrsRegionEncoder.encodeLocations(source));
    }
}
