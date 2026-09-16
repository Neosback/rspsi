package com.rspsi.cache.map;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OsrsRegionDecoderTest {
    @Test
    void decodesTerrainSemanticsAndFourPlaneHeightInheritance() {
        byte[] data = terrainFixture();

        WorldDocument document = OsrsRegionDecoder.decodeTerrain(data, 10, 20, (x, y) -> 10);
        TileSnapshot ground = document.tile(0, 0, 0).snapshot();
        TileSnapshot upper = document.tile(1, 0, 0).snapshot();

        assertEquals(-40, ground.southWestHeight());
        assertEquals(-80, ground.southEastHeight());
        assertEquals(3, ground.overlayId());
        assertEquals(6, ground.flags());
        assertEquals(7, ground.underlayId());
        assertEquals(6, ground.overlayShape());
        assertEquals(3, ground.overlayRotation());
        assertEquals(-56, upper.southWestHeight());
        assertEquals(-320, document.tile(1, 1, 0).snapshot().southWestHeight());
    }

    @Test
    void decodesDeltaPackedLocationsFromFileOne() {
        WorldObject object = OsrsRegionDecoder.decodeLocations(locationFixture()).get(0);

        assertEquals(new WorldObject(100, 10, 2, 1, 3, 4), object);
    }

    @Test
    void combinesTerrainAndLocationsInTheCanonicalDocument() {
        WorldDocument document = OsrsRegionDecoder.decode(terrainFixture(), locationFixture(), 0, 0);

        assertEquals(List.of(new WorldObject(100, 10, 2, 1, 3, 4)),
                document.tile(1, 3, 4).snapshot().objects());
    }

    @Test
    void rejectsTruncatedTerrainPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> OsrsRegionDecoder.decodeTerrain(new byte[]{0}, 0, 0, (x, y) -> 0));
    }

    private static byte[] terrainFixture() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int plane = 0; plane < OsrsRegionDecoder.PLANES; plane++) {
            for (int x = 0; x < OsrsRegionDecoder.REGION_SIZE; x++) {
                for (int y = 0; y < OsrsRegionDecoder.REGION_SIZE; y++) {
                    if (plane == 0 && x == 0 && y == 0) {
                        writeShort(out, 29); // shape 6, rotation 3
                        writeShort(out, 4);  // canonical overlay id 3
                        writeShort(out, 88); // underlay id 7
                        writeShort(out, 55); // flags 0x06
                        writeShort(out, 1);
                        out.write(5);
                    } else if (plane == 1 && x == 0 && y == 0) {
                        writeShort(out, 1);
                        out.write(2);
                    } else {
                        writeShort(out, 0);
                    }
                }
            }
        }
        return out.toByteArray();
    }

    private static byte[] locationFixture() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeUnsignedSmart(out, 101); // id delta from -1 -> 100
        int packedPosition = (1 << 12) | (3 << 6) | 4;
        writeUnsignedSmart(out, packedPosition + 1);
        out.write((10 << 2) | 2);
        out.write(0); // end locations for this object id
        out.write(0); // end object ids
        return out.toByteArray();
    }

    private static void writeUnsignedSmart(ByteArrayOutputStream out, int value) {
        if (value < 128) {
            out.write(value);
        } else {
            writeShort(out, value + 0x8000);
        }
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
