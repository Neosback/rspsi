package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.editor.model.InstanceChunkGrid;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Independent terrain and object semantics exported from a reference instance scene. */
public record OsrsInstanceSemanticFixture(
        int formatVersion,
        int width,
        int length,
        int planes,
        int sourceRegionX,
        int sourceRegionY,
        int[][][] templates,
        int[] heights,
        int[] underlays,
        int[] overlays,
        int[] shapes,
        int[] rotations,
        int[] flags,
        List<WorldObject> objects
) {
    public static final int FORMAT_VERSION = 1;

    public OsrsInstanceSemanticFixture {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported instance fixture version: " + formatVersion);
        }
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Instance fixture dimensions must be positive");
        }
        templates = copyTemplates(templates);
        int tileCount = Math.multiplyExact(Math.multiplyExact(width, length), planes);
        heights = copyAndRequire(heights, tileCount, "heights");
        underlays = copyAndRequire(underlays, tileCount, "underlays");
        overlays = copyAndRequire(overlays, tileCount, "overlays");
        shapes = copyAndRequire(shapes, tileCount, "shapes");
        rotations = copyAndRequire(rotations, tileCount, "rotations");
        flags = copyAndRequire(flags, tileCount, "flags");
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
    }

    public static OsrsInstanceSemanticFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) throw new IOException("Instance fixture does not exist: " + path);
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        List<WorldObject> objects = new ArrayList<>();
        JsonElement encodedObjects = root.get("objects");
        if (encodedObjects != null && encodedObjects.isJsonArray()) {
            for (JsonElement element : encodedObjects.getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                objects.add(new WorldObject(requiredInt(object, "id"), requiredInt(object, "type"),
                        requiredInt(object, "rotation"), requiredInt(object, "plane"),
                        requiredInt(object, "x"), requiredInt(object, "y")));
            }
        }
        return new OsrsInstanceSemanticFixture(
                requiredInt(root, "formatVersion"), requiredInt(root, "width"),
                requiredInt(root, "length"), requiredInt(root, "planes"),
                requiredInt(root, "sourceRegionX"), requiredInt(root, "sourceRegionY"),
                requiredTemplates(root, "templates"), requiredArray(root, "heights"),
                requiredArray(root, "underlays"), requiredArray(root, "overlays"),
                requiredArray(root, "shapes"), requiredArray(root, "rotations"),
                requiredArray(root, "flags"), objects);
    }

    public InstanceChunkGrid grid() {
        return InstanceChunkGrid.decode(templates, 0, 0);
    }

    public Comparison compareTerrain(WorldDocument document) {
        if (document.width() != width || document.length() != length || document.planes() != planes) {
            return new Comparison(false, 1, List.of("document dimensions do not match instance fixture"));
        }
        int differences = 0;
        List<String> samples = new ArrayList<>(8);
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    int index = tileIndex(plane, x, y);
                    differences += check(tile.southWestHeight(), heights[index], "height", plane, x, y, samples);
                    differences += check(tile.underlayId(), underlays[index], "underlay", plane, x, y, samples);
                    differences += check(tile.overlayId(), overlays[index], "overlay", plane, x, y, samples);
                    differences += check(tile.overlayShape(), shapes[index], "shape", plane, x, y, samples);
                    differences += check(tile.overlayRotation(), rotations[index], "rotation", plane, x, y, samples);
                    differences += check(tile.flags(), flags[index], "flags", plane, x, y, samples);
                }
            }
        }
        return new Comparison(differences == 0, differences, samples);
    }

    public OsrsLocationSemanticFixture.Comparison compareObjects(WorldDocument document) {
        return new OsrsLocationSemanticFixture(OsrsLocationSemanticFixture.FORMAT_VERSION, objects)
                .compare(document);
    }

    private static int check(int actual, int expected, String field, int plane, int x, int y,
                             List<String> samples) {
        if (actual == expected) return 0;
        if (samples.size() < 8) {
            samples.add(field + " at " + plane + "," + x + "," + y
                    + " expected=" + expected + " actual=" + actual);
        }
        return 1;
    }

    private static int tileIndex(int plane, int x, int y, int width, int length) {
        return (plane * width + x) * length + y;
    }

    private int tileIndex(int plane, int x, int y) {
        return tileIndex(plane, x, y, width, length);
    }

    private static int[][][] copyTemplates(int[][][] input) {
        Objects.requireNonNull(input, "templates");
        int[][][] copy = new int[input.length][][];
        for (int plane = 0; plane < input.length; plane++) {
            if (input[plane] == null) throw new IllegalArgumentException("Null template plane");
            copy[plane] = new int[input[plane].length][];
            for (int x = 0; x < input[plane].length; x++) {
                copy[plane][x] = Objects.requireNonNull(input[plane][x], "template row").clone();
            }
        }
        return copy;
    }

    private static int[][][] requiredTemplates(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing " + name);
        JsonArray planes = value.getAsJsonArray();
        int[][][] result = new int[planes.size()][][];
        for (int p = 0; p < planes.size(); p++) {
            JsonArray rows = planes.get(p).getAsJsonArray();
            result[p] = new int[rows.size()][];
            for (int x = 0; x < rows.size(); x++) {
                JsonArray columns = rows.get(x).getAsJsonArray();
                result[p][x] = new int[columns.size()];
                for (int y = 0; y < columns.size(); y++) result[p][x][y] = columns.get(y).getAsInt();
            }
        }
        return result;
    }

    private static int[] requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing " + name);
        JsonArray array = value.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = array.get(i).getAsInt();
        return result;
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing integer " + name);
        return value.getAsInt();
    }

    private static int[] copyAndRequire(int[] input, int expected, String name) {
        if (input == null || input.length != expected) {
            throw new IllegalArgumentException(name + " must contain " + expected + " values");
        }
        return input.clone();
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
