package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Independent location placements exported from a reference scene builder. */
public record OsrsLocationSemanticFixture(
        int formatVersion,
        List<WorldObject> objects
) {
    public static final int FORMAT_VERSION = 1;

    public OsrsLocationSemanticFixture {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported location fixture version: " + formatVersion);
        }
        objects = sortedCopy(objects);
    }

    public static OsrsLocationSemanticFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Location fixture does not exist: " + path);
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonElement encoded = root.get("objects");
        if (encoded == null || !encoded.isJsonArray()) {
            throw new IllegalArgumentException("Location fixture is missing objects array");
        }
        List<WorldObject> objects = new ArrayList<>();
        for (JsonElement element : encoded.getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            objects.add(new WorldObject(
                    requiredInt(object, "id"),
                    requiredInt(object, "type"),
                    requiredInt(object, "rotation"),
                    requiredInt(object, "plane"),
                    requiredInt(object, "x"),
                    requiredInt(object, "y")));
        }
        return new OsrsLocationSemanticFixture(requiredInt(root, "formatVersion"), objects);
    }

    /** Compares every canonical placement in the 64x64 region. */
    public Comparison compare(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        List<WorldObject> actual = new ArrayList<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    actual.addAll(document.tile(plane, x, y).snapshot().objects());
                }
            }
        }
        actual = sortedCopy(actual);
        if (!objects.equals(actual)) {
            int firstDifference = firstDifference(objects, actual);
            List<String> samples = new ArrayList<>(3);
            samples.add("counts expected=" + objects.size() + " actual=" + actual.size());
            if (firstDifference < objects.size()) {
                samples.add("expected[" + firstDifference + "]=" + objects.get(firstDifference));
            }
            if (firstDifference < actual.size()) {
                samples.add("actual[" + firstDifference + "]=" + actual.get(firstDifference));
            }
            return new Comparison(false, Math.abs(objects.size() - actual.size())
                    + (objects.size() == actual.size() ? 1 : 0), List.copyOf(samples));
        }
        return new Comparison(true, 0, List.of());
    }

    private static int firstDifference(List<WorldObject> expected, List<WorldObject> actual) {
        int limit = Math.min(expected.size(), actual.size());
        for (int i = 0; i < limit; i++) {
            if (!expected.get(i).equals(actual.get(i))) return i;
        }
        return limit;
    }

    private static List<WorldObject> sortedCopy(List<WorldObject> values) {
        if (values == null) throw new IllegalArgumentException("Location fixture objects cannot be null");
        List<WorldObject> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparingInt(WorldObject::id)
                .thenComparingInt(WorldObject::plane)
                .thenComparingInt(WorldObject::x)
                .thenComparingInt(WorldObject::y)
                .thenComparingInt(WorldObject::type)
                .thenComparingInt(WorldObject::rotation));
        return List.copyOf(copy);
    }

    private static int requiredInt(JsonObject root, String name) {
        JsonElement value = root.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Location fixture is missing integer field: " + name);
        }
        return value.getAsInt();
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
