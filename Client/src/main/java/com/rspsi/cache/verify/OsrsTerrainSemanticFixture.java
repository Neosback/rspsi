package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Independent terrain semantics exported by a reference client such as TSPS.
 *
 * <p>The fixture intentionally stores tile semantics rather than RSPSi's
 * render fingerprint. That keeps the comparison useful across renderers while
 * still proving that two implementations interpreted the same cache bytes in
 * the same way.</p>
 */
public record OsrsTerrainSemanticFixture(
        int formatVersion,
        int width,
        int length,
        int planes,
        int[] heights,
        int[] underlays,
        int[] overlays,
        int[] shapes,
        int[] rotations,
        int[] flags
) {
    public static final int FORMAT_VERSION = 1;
    public static final int REGION_SIZE = 64;
    public static final int PLANE_COUNT = 4;

    public OsrsTerrainSemanticFixture {
        heights = copyAndRequire(heights, heightCount(width, length, planes), "heights");
        underlays = copyAndRequire(underlays, tileCount(width, length, planes), "underlays");
        overlays = copyAndRequire(overlays, tileCount(width, length, planes), "overlays");
        shapes = copyAndRequire(shapes, tileCount(width, length, planes), "shapes");
        rotations = copyAndRequire(rotations, tileCount(width, length, planes), "rotations");
        flags = copyAndRequire(flags, tileCount(width, length, planes), "flags");
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported terrain fixture version: " + formatVersion);
        }
        if (width != REGION_SIZE || length != REGION_SIZE || planes != PLANE_COUNT) {
            throw new IllegalArgumentException("Terrain fixture must be 64x64x4");
        }
    }

    public static OsrsTerrainSemanticFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Terrain fixture does not exist: " + path);
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return new OsrsTerrainSemanticFixture(
                requiredInt(root, "formatVersion"),
                requiredInt(root, "width"),
                requiredInt(root, "length"),
                requiredInt(root, "planes"),
                requiredArray(root, "heights"),
                requiredArray(root, "underlays"),
                requiredArray(root, "overlays"),
                requiredArray(root, "shapes"),
                requiredArray(root, "rotations"),
                requiredArray(root, "flags"));
    }

    /** Compares every canonical terrain field and returns bounded diagnostics. */
    public Comparison compare(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        if (document.width() != width || document.length() != length || document.planes() != planes) {
            return new Comparison(false, 1, List.of("document dimensions do not match fixture"));
        }
        int differences = 0;
        List<String> samples = new ArrayList<>(8);
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    int tileIndex = tileIndex(plane, x, y);
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    check(tile.southWestHeight(), heights[heightIndex(plane, x, y)],
                            "height", plane, x, y, samples);
                    check(tile.underlayId(), underlays[tileIndex], "underlay", plane, x, y, samples);
                    check(tile.overlayId(), overlays[tileIndex], "overlay", plane, x, y, samples);
                    check(tile.overlayShape(), shapes[tileIndex], "shape", plane, x, y, samples);
                    check(tile.overlayRotation(), rotations[tileIndex], "rotation", plane, x, y, samples);
                    check(tile.flags(), flags[tileIndex], "flags", plane, x, y, samples);
                }
            }
        }
        // The helper records one diagnostic only when a field differs. Count
        // is recomputed independently so samples remain bounded and readable.
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    int tileIndex = tileIndex(plane, x, y);
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    differences += mismatch(tile.southWestHeight(), heights[heightIndex(plane, x, y)]);
                    differences += mismatch(tile.underlayId(), underlays[tileIndex]);
                    differences += mismatch(tile.overlayId(), overlays[tileIndex]);
                    differences += mismatch(tile.overlayShape(), shapes[tileIndex]);
                    differences += mismatch(tile.overlayRotation(), rotations[tileIndex]);
                    differences += mismatch(tile.flags(), flags[tileIndex]);
                }
            }
        }
        return new Comparison(differences == 0, differences, List.copyOf(samples));
    }

    private static void check(int actual, int expected, String field, int plane, int x, int y,
                              List<String> samples) {
        if (actual != expected && samples.size() < 8) {
            samples.add(field + " at " + plane + "," + x + "," + y
                    + " expected=" + expected + " actual=" + actual);
        }
    }

    private static int mismatch(int actual, int expected) {
        return actual == expected ? 0 : 1;
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Terrain fixture is missing integer field: " + name);
        }
        return value.getAsInt();
    }

    private static int[] requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Terrain fixture is missing array field: " + name);
        }
        JsonArray array = value.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.get(i).getAsInt();
        }
        return result;
    }

    private static int[] copyAndRequire(int[] values, int expected, String name) {
        if (values == null || values.length != expected) {
            throw new IllegalArgumentException(name + " must contain " + expected + " values");
        }
        return values.clone();
    }

    private static int tileCount(int width, int length, int planes) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Terrain fixture dimensions must be positive");
        }
        return Math.multiplyExact(Math.multiplyExact(width, length), planes);
    }

    private static int heightCount(int width, int length, int planes) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Terrain fixture dimensions must be positive");
        }
        return Math.multiplyExact(Math.multiplyExact(width, length), planes);
    }

    private static int tileIndex(int plane, int x, int y) {
        return (plane * REGION_SIZE + x) * REGION_SIZE + y;
    }

    private static int heightIndex(int plane, int x, int y) {
        return (plane * REGION_SIZE + x) * REGION_SIZE + y;
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
