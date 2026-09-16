package com.rspsi.cache.map;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Neutral decoder for the OSRS map and location archive payloads.
 *
 * <p>The byte format is deliberately kept here, at the cache boundary. The
 * editor model receives semantic terrain and objects and never needs to know
 * about archive files, opcodes, or cache-library classes. The opcode handling
 * follows the OpenRune map decoder and the existing RSPSi terrain height
 * semantics.</p>
 */
public final class OsrsRegionDecoder {
    public static final int REGION_SIZE = 64;
    public static final int PLANES = 4;

    private OsrsRegionDecoder() {
    }

    @FunctionalInterface
    public interface BaseHeightProvider {
        /** Returns the unscaled RuneScape height value at world coordinates. */
        int heightAt(int worldX, int worldY);
    }

    /** Decodes terrain using the classic deterministic base-height function. */
    public static WorldDocument decodeTerrain(byte[] data, int regionX, int regionY) {
        return decodeTerrain(data, regionX, regionY, OsrsRegionDecoder::defaultBaseHeight);
    }

    /**
     * Decodes one 64x64x4 landscape archive into the canonical document.
     * A provider is injectable so fixtures can use a flat base and parity tests
     * can compare another client’s base-height implementation exactly.
     */
    public static WorldDocument decodeTerrain(
            byte[] data,
            int regionX,
            int regionY,
            BaseHeightProvider baseHeightProvider
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(baseHeightProvider, "baseHeightProvider");

        WorldDocument document = new WorldDocument(REGION_SIZE, REGION_SIZE, PLANES);
        int[][][] heights = new int[PLANES][REGION_SIZE + 1][REGION_SIZE + 1];
        int[][][] underlays = new int[PLANES][REGION_SIZE][REGION_SIZE];
        int[][][] overlays = new int[PLANES][REGION_SIZE][REGION_SIZE];
        int[][][] shapes = new int[PLANES][REGION_SIZE][REGION_SIZE];
        int[][][] rotations = new int[PLANES][REGION_SIZE][REGION_SIZE];
        int[][][] flags = new int[PLANES][REGION_SIZE][REGION_SIZE];
        Cursor cursor = new Cursor(data);

        for (int plane = 0; plane < PLANES; plane++) {
            for (int x = 0; x < REGION_SIZE; x++) {
                for (int y = 0; y < REGION_SIZE; y++) {
                    decodeTile(cursor, plane, x, y, regionX, regionY,
                            baseHeightProvider, heights, underlays, overlays,
                            shapes, rotations, flags);
                }
            }
        }

        // A standalone region owns the lower-left corner of each tile. The
        // final row/column are the shared border vertices for mesh building.
        for (int plane = 0; plane < PLANES; plane++) {
            for (int x = 0; x < REGION_SIZE; x++) {
                heights[plane][x][REGION_SIZE] = heights[plane][x][REGION_SIZE - 1];
            }
            for (int y = 0; y <= REGION_SIZE; y++) {
                heights[plane][REGION_SIZE][y] = heights[plane][REGION_SIZE - 1][y];
            }
        }

        for (int plane = 0; plane < PLANES; plane++) {
            for (int x = 0; x < REGION_SIZE; x++) {
                for (int y = 0; y < REGION_SIZE; y++) {
                    document.tile(plane, x, y).restore(new TileSnapshot(
                            heights[plane][x][y],
                            heights[plane][x + 1][y],
                            heights[plane][x + 1][y + 1],
                            heights[plane][x][y + 1],
                            underlays[plane][x][y],
                            overlays[plane][x][y],
                            shapes[plane][x][y],
                            rotations[plane][x][y],
                            flags[plane][x][y],
                            List.of()));
                }
            }
        }
        return document;
    }

    /** Decodes the delta-packed file-1 location payload for one region. */
    public static List<WorldObject> decodeLocations(byte[] data) {
        Objects.requireNonNull(data, "data");
        Cursor cursor = new Cursor(data);
        List<WorldObject> objects = new ArrayList<>();
        int objectId = -1;

        while (cursor.remaining() > 0) {
            int objectDelta = readIncrementalSmart(cursor);
            if (objectDelta == 0) {
                break;
            }
            objectId += objectDelta;
            int packedPosition = 0;
            while (cursor.remaining() > 0) {
                int positionDelta = readUnsignedSmart(cursor);
                if (positionDelta == 0) {
                    break;
                }
                packedPosition += positionDelta - 1;
                int attributes = cursor.readUnsignedByte();
                int plane = (packedPosition >>> 12) & 0x3;
                int x = (packedPosition >>> 6) & 0x3F;
                int y = packedPosition & 0x3F;
                int type = attributes >>> 2;
                int rotation = attributes & 0x3;
                objects.add(new WorldObject(objectId, type, rotation, plane, x, y));
            }
        }
        return List.copyOf(objects);
    }

    /** Decodes terrain and attaches locations to their owning canonical tiles. */
    public static WorldDocument decode(byte[] landscape, byte[] locations, int regionX, int regionY) {
        return decode(landscape, locations, regionX, regionY, OsrsRegionDecoder::defaultBaseHeight);
    }

    /** Decodes a region while retaining its canonical world identity. */
    public static WorldRegion decodeRegion(byte[] landscape, byte[] locations, int regionX, int regionY) {
        return decodeRegion(landscape, locations, regionX, regionY, OsrsRegionDecoder::defaultBaseHeight);
    }

    /** Decodes a region with an injectable base-height provider for parity tests. */
    public static WorldRegion decodeRegion(byte[] landscape, byte[] locations, int regionX, int regionY,
                                           BaseHeightProvider baseHeightProvider) {
        return new WorldRegion(regionX, regionY,
                decode(landscape, locations == null ? new byte[0] : locations,
                        regionX, regionY, baseHeightProvider));
    }

    /** Combines both archive payloads with an injectable base-height provider. */
    public static WorldDocument decode(
            byte[] landscape,
            byte[] locations,
            int regionX,
            int regionY,
            BaseHeightProvider baseHeightProvider
    ) {
        WorldDocument document = decodeTerrain(landscape, regionX, regionY, baseHeightProvider);
        for (WorldObject object : decodeLocations(locations)) {
            TileSnapshot before = document.tile(object.plane(), object.x(), object.y()).snapshot();
            List<WorldObject> objects = new ArrayList<>(before.objects());
            objects.add(object);
            document.tile(object.plane(), object.x(), object.y()).restore(new TileSnapshot(
                    before.southWestHeight(), before.southEastHeight(), before.northEastHeight(),
                    before.northWestHeight(), before.underlayId(), before.overlayId(),
                    before.overlayShape(), before.overlayRotation(), before.flags(), objects));
        }
        return document;
    }

    private static void decodeTile(
            Cursor cursor,
            int plane,
            int x,
            int y,
            int regionX,
            int regionY,
            BaseHeightProvider baseHeightProvider,
            int[][][] heights,
            int[][][] underlays,
            int[][][] overlays,
            int[][][] shapes,
            int[][][] rotations,
            int[][][] flags
    ) {
        while (true) {
            int opcode = cursor.readUnsignedShort();
            if (opcode == 0 || opcode == 1) {
                int value = opcode == 1 ? cursor.readUnsignedByte() : 0;
                if (plane == 0) {
                    heights[plane][x][y] = opcode == 0
                            ? -baseHeightProvider.heightAt(regionX * REGION_SIZE + x, regionY * REGION_SIZE + y) * 8
                            : -normaliseExplicitHeight(value) * 8;
                } else {
                    int previous = heights[plane - 1][x][y];
                    heights[plane][x][y] = opcode == 0
                            ? previous - 240
                            : previous - normaliseExplicitHeight(value) * 8;
                }
                return;
            }
            if (opcode <= 49) {
                int rawOverlay = cursor.readShort();
                overlays[plane][x][y] = (rawOverlay - 1) & 0xFFFF;
                shapes[plane][x][y] = (opcode - 2) >>> 2;
                rotations[plane][x][y] = (opcode - 2) & 0x3;
            } else if (opcode <= 81) {
                flags[plane][x][y] = opcode - 49;
            } else {
                underlays[plane][x][y] = (opcode - 81) & 0xFF;
            }
        }
    }

    private static int normaliseExplicitHeight(int value) {
        return value == 1 ? 0 : value;
    }

    private static int readIncrementalSmart(Cursor cursor) {
        int total = 0;
        int value;
        do {
            value = readUnsignedSmart(cursor);
            total += value;
        } while (value == 32767);
        return total;
    }

    private static int readUnsignedSmart(Cursor cursor) {
        return (cursor.peekUnsignedByte() < 128)
                ? cursor.readUnsignedByte()
                : cursor.readUnsignedShort() - 0x8000;
    }

    private static int defaultBaseHeight(int worldX, int worldY) {
        int height = interpolatedNoise(worldX + 45365, worldY + 0x16713, 4) - 128
                + ((interpolatedNoise(worldX + 10294, worldY + 37821, 2) - 128) >> 1)
                + ((interpolatedNoise(worldX, worldY, 1) - 128) >> 2);
        height = (int) (height * 0.3D) + 35;
        return Math.max(10, Math.min(60, height));
    }

    private static int interpolatedNoise(int x, int y, int scale) {
        int sampleX = x / scale;
        int offsetX = x & (scale - 1);
        int sampleY = y / scale;
        int offsetY = y & (scale - 1);
        int a = smoothNoise(sampleX, sampleY);
        int b = smoothNoise(sampleX + 1, sampleY);
        int c = smoothNoise(sampleX, sampleY + 1);
        int d = smoothNoise(sampleX + 1, sampleY + 1);
        return interpolate(interpolate(a, b, offsetX, scale),
                interpolate(c, d, offsetX, scale), offsetY, scale);
    }

    private static int interpolate(int a, int b, int offset, int scale) {
        int cosine = 0x10000 - (int) (65536D * Math.cos(offset * Math.PI / (1024D * scale))) >> 1;
        return (a * (0x10000 - cosine) >> 16) + (b * cosine >> 16);
    }

    private static int smoothNoise(int x, int y) {
        int corners = noise(x - 1, y - 1) + noise(x + 1, y - 1)
                + noise(x - 1, y + 1) + noise(x + 1, y + 1);
        int sides = noise(x - 1, y) + noise(x + 1, y)
                + noise(x, y - 1) + noise(x, y + 1);
        return corners / 16 + sides / 8 + noise(x, y) / 4;
    }

    private static int noise(int x, int y) {
        int value = x + y * 57;
        value = value << 13 ^ value;
        value = value * (value * value * 15731 + 0xC0AE5) + 0x5208DD0D & 0x7FFFFFFF;
        return value >> 19 & 0xFF;
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private int remaining() {
            return data.length - position;
        }

        private int peekUnsignedByte() {
            require(1);
            return data[position] & 0xFF;
        }

        private int readUnsignedByte() {
            require(1);
            return data[position++] & 0xFF;
        }

        private int readUnsignedShort() {
            require(2);
            return (data[position++] & 0xFF) << 8 | data[position++] & 0xFF;
        }

        private int readShort() {
            int value = readUnsignedShort();
            return value > 0x7FFF ? value - 0x10000 : value;
        }

        private void require(int bytes) {
            if (remaining() < bytes) {
                throw new IllegalArgumentException("Truncated OSRS map payload at byte " + position);
            }
        }
    }
}
