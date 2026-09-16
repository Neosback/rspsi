package com.rspsi.cache.map;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Encodes canonical region state into neutral OSRS landscape/location bytes. */
public final class OsrsRegionEncoder {
    private OsrsRegionEncoder() {
    }

    /**
     * Encodes the terrain file. Heights are written explicitly; this is
     * intentional because semantic equality matters more than preserving
     * whether a source tile used generated height opcode 0.
     */
    public static byte[] encodeTerrain(WorldDocument document) {
        requireRegion(document);
        requireSharedHeights(document);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int plane = 0; plane < OsrsRegionDecoder.PLANES; plane++) {
            for (int x = 0; x < OsrsRegionDecoder.REGION_SIZE; x++) {
                for (int y = 0; y < OsrsRegionDecoder.REGION_SIZE; y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    if (tile.overlayId() != 0) {
                        requireRange(tile.overlayId(), 1, 65534, "overlay ID");
                        requireRange(tile.overlayShape(), 0, 11, "overlay shape");
                        writeShort(out, 2 + tile.overlayShape() * 4 + tile.overlayRotation());
                        writeShort(out, tile.overlayId() + 1);
                    } else if (tile.overlayShape() != 0 || tile.overlayRotation() != 0) {
                        throw new IllegalArgumentException("Overlay shape/rotation requires an overlay ID");
                    }
                    if (tile.flags() != 0) {
                        requireRange(tile.flags(), 1, 32, "tile flags");
                        writeShort(out, 49 + tile.flags());
                    }
                    if (tile.underlayId() != 0) {
                        requireRange(tile.underlayId(), 1, 255, "underlay ID");
                        writeShort(out, 81 + tile.underlayId());
                    }
                    int value = heightValue(document, plane, x, y, tile.southWestHeight());
                    writeShort(out, 1);
                    out.write(value);
                }
            }
        }
        return out.toByteArray();
    }

    /** Encodes the delta-packed file-1 location payload. */
    public static byte[] encodeLocations(WorldDocument document) {
        requireRegion(document);
        List<WorldObject> objects = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    for (WorldObject object : document.tile(plane, x, y).snapshot().objects()) {
                        if (object.plane() != plane || object.x() != x || object.y() != y) {
                            throw new IllegalArgumentException("Object is not owned by its document tile: " + object);
                        }
                        requireRange(object.type(), 0, 63, "object type");
                        objects.add(object);
                    }
                }
            }
        }
        objects.sort(Comparator
                .comparingInt(WorldObject::id)
                .thenComparingInt(OsrsRegionEncoder::packedPosition)
                .thenComparingInt(object -> (object.type() << 2) | object.rotation()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previousId = -1;
        int index = 0;
        while (index < objects.size()) {
            int objectId = objects.get(index).id();
            writeIncrementalSmart(out, objectId - previousId);
            previousId = objectId;
            int previousPosition = 0;
            while (index < objects.size() && objects.get(index).id() == objectId) {
                WorldObject object = objects.get(index++);
                int position = packedPosition(object);
                writeUnsignedSmart(out, position - previousPosition + 1);
                out.write((object.type() << 2) | object.rotation());
                previousPosition = position;
            }
            writeUnsignedSmart(out, 0);
        }
        writeUnsignedSmart(out, 0);
        return out.toByteArray();
    }

    private static int heightValue(WorldDocument document, int plane, int x, int y, int height) {
        if ((height & 7) != 0) {
            throw new IllegalArgumentException("Tile height must be divisible by 8: " + height);
        }
        int value;
        if (plane == 0) {
            value = -height / 8;
        } else {
            int previous = document.tile(plane - 1, x, y).snapshot().southWestHeight();
            value = (previous - height) / 8;
        }
        requireRange(value, 0, 255, "height value");
        return value;
    }

    private static int packedPosition(WorldObject object) {
        return (object.plane() << 12) | (object.x() << 6) | object.y();
    }

    private static void writeIncrementalSmart(ByteArrayOutputStream out, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Object IDs must be strictly increasing and non-negative");
        }
        int remaining = value;
        while (remaining >= 32767) {
            writeUnsignedSmart(out, 32767);
            remaining -= 32767;
        }
        writeUnsignedSmart(out, remaining);
    }

    private static void writeUnsignedSmart(ByteArrayOutputStream out, int value) {
        requireRange(value, 0, 32767, "smart value");
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

    private static void requireRegion(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        if (document.width() != OsrsRegionDecoder.REGION_SIZE
                || document.length() != OsrsRegionDecoder.REGION_SIZE
                || document.planes() != OsrsRegionDecoder.PLANES) {
            throw new IllegalArgumentException("OSRS region must be exactly 64x64x4");
        }
    }

    /** Prevents encoding a terrain document whose vertex graph already has cracks. */
    private static void requireSharedHeights(WorldDocument document) {
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    if (x + 1 < document.width()) {
                        TileSnapshot east = document.tile(plane, x + 1, y).snapshot();
                        if (tile.southEastHeight() != east.southWestHeight()
                                || tile.northEastHeight() != east.northWestHeight()) {
                            throw new IllegalArgumentException("Terrain east edge heights do not match at "
                                    + plane + "," + x + "," + y);
                        }
                    }
                    if (y + 1 < document.length()) {
                        TileSnapshot north = document.tile(plane, x, y + 1).snapshot();
                        if (tile.northWestHeight() != north.southWestHeight()
                                || tile.northEastHeight() != north.southEastHeight()) {
                            throw new IllegalArgumentException("Terrain north edge heights do not match at "
                                    + plane + "," + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
    }
}
