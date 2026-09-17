package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.editor.collision.CollisionMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Independent OSRS collision flags exported from a reference scene builder. */
public record OsrsCollisionSemanticFixture(int formatVersion, int width, int length,
                                           int planes, int[] flags) {
    public static final int FORMAT_VERSION = 1;

    public OsrsCollisionSemanticFixture {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported collision fixture version: " + formatVersion);
        }
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("Collision fixture dimensions must be positive");
        }
        if (flags == null || flags.length != width * length * planes) {
            throw new IllegalArgumentException("Collision fixture has the wrong flag count");
        }
        flags = flags.clone();
    }

    public static OsrsCollisionSemanticFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) throw new IOException("Collision fixture does not exist: " + path);
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return new OsrsCollisionSemanticFixture(
                requiredInt(root, "formatVersion"),
                requiredInt(root, "width"),
                requiredInt(root, "length"),
                requiredInt(root, "planes"),
                requiredArray(root, "flags"));
    }

    /** Compares the reference's interior collision fields; scene borders are backend policy. */
    public Comparison compare(CollisionMap actual) {
        Objects.requireNonNull(actual, "actual");
        if (actual.width() != width || actual.length() != length || actual.planes() != planes) {
            return new Comparison(false, 1, List.of("collision dimensions do not match fixture"));
        }
        int differences = 0;
        List<String> samples = new ArrayList<>(8);
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 1; x < width - 1; x++) {
                for (int y = 1; y < length - 1; y++) {
                    int expected = flags[index(plane, x, y)];
                    int value = actual.flags(plane, x, y);
                    if (expected == value) continue;
                    differences++;
                    if (samples.size() < 8) {
                        samples.add("at " + plane + "," + x + "," + y
                                + " expected=0x" + Integer.toHexString(expected)
                                + " actual=0x" + Integer.toHexString(value));
                    }
                }
            }
        }
        return new Comparison(differences == 0, differences, List.copyOf(samples));
    }

    private int index(int plane, int x, int y) {
        return (plane * width + x) * length + y;
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Collision fixture is missing integer field: " + name);
        }
        return value.getAsInt();
    }

    private static int[] requiredArray(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException("Collision fixture is missing array field: " + name);
        }
        JsonArray array = value.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = array.get(i).getAsInt();
        return result;
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
