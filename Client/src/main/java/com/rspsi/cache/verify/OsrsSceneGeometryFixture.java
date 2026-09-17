package com.rspsi.cache.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.terrain.TerrainFace;
import com.rspsi.editor.terrain.TerrainMesh;
import com.rspsi.editor.terrain.TerrainMeshBuilder;
import com.rspsi.editor.terrain.TerrainVertex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Independent terrain geometry exported by a reference scene builder.
 * Materials and renderer colors are intentionally excluded; this fixture
 * verifies the shared geometry contract without coupling either renderer's
 * internal color representation to the other.
 */
public record OsrsSceneGeometryFixture(int formatVersion, List<TileGeometry> tiles) {
    public static final int FORMAT_VERSION = 1;

    public OsrsSceneGeometryFixture {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported scene geometry fixture version: " + formatVersion);
        }
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
    }

    public static OsrsSceneGeometryFixture load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Scene geometry fixture does not exist: " + path);
        }
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonElement encodedTiles = root.get("tiles");
        if (encodedTiles == null || !encodedTiles.isJsonArray()) {
            throw new IllegalArgumentException("Scene geometry fixture is missing tiles array");
        }
        List<TileGeometry> tiles = new ArrayList<>();
        for (JsonElement element : encodedTiles.getAsJsonArray()) {
            JsonObject tile = element.getAsJsonObject();
            tiles.add(new TileGeometry(
                    requiredInt(tile, "plane"),
                    requiredInt(tile, "x"),
                    requiredInt(tile, "y"),
                    vertices(tile.get("vertices")),
                    faces(tile.get("faces"))));
        }
        return new OsrsSceneGeometryFixture(requiredInt(root, "formatVersion"), tiles);
    }

    public Comparison compare(WorldDocument document) {
        return compare(document, OsrsScenePlaneMode.AUTHORED);
    }

    public Comparison compare(WorldDocument document, OsrsScenePlaneMode planeMode) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(planeMode, "planeMode");
        TerrainMeshBuilder builder = new TerrainMeshBuilder();
        int differences = 0;
        List<String> samples = new ArrayList<>(8);
        for (TileGeometry expected : tiles) {
            int authoredPlane = planeMode == OsrsScenePlaneMode.EFFECTIVE
                    ? authoredPlaneForEffectiveTile(document, expected.plane(), expected.x(), expected.y())
                    : expected.plane();
            if (authoredPlane >= document.planes()
                    || expected.x() >= document.width() || expected.y() >= document.length()) {
                differences++;
                addSample(samples, "fixture tile outside document: " + expected.coordinate());
                continue;
            }
            TerrainMesh actual = builder.build(document.tile(authoredPlane, expected.x(), expected.y()).snapshot());
            if (!expected.matches(actual)) {
                differences++;
                addSample(samples, "geometry differs at " + expected.coordinate()
                        + " expectedVertices=" + expected.vertices().size()
                        + " actualVertices=" + actual.vertices().size()
                        + " expectedFaces=" + expected.faces().size()
                        + " actualFaces=" + actual.faces().size());
            }
        }
        return new Comparison(differences == 0, differences, List.copyOf(samples));
    }

    /**
     * Effective scene exports describe post-relink render planes. The document
     * retains authored cache planes, so invert the bridge shift before building
     * the comparable terrain mesh.
     */
    private static int authoredPlaneForEffectiveTile(WorldDocument document, int effectivePlane,
                                                       int x, int y) {
        if (effectivePlane < 0 || effectivePlane >= document.planes()
                || x < 0 || x >= document.width() || y < 0 || y >= document.length()) {
            return effectivePlane;
        }
        boolean bridge = document.planes() > 1
                && OsrsTileFlags.hasBridge(document.tile(1, x, y).snapshot().flags());
        return bridge && effectivePlane < document.planes() - 1
                ? effectivePlane + 1 : effectivePlane;
    }

    private static void addSample(List<String> samples, String value) {
        if (samples.size() < 8) samples.add(value);
    }

    private static List<TerrainVertex> vertices(JsonElement encoded) {
        int[] values = flatValues(encoded, "vertices");
        if (values.length % 3 != 0) throw new IllegalArgumentException("vertices must be triples");
        List<TerrainVertex> result = new ArrayList<>();
        for (int i = 0; i < values.length; i += 3) {
            result.add(new TerrainVertex(values[i], values[i + 1], values[i + 2]));
        }
        return List.copyOf(result);
    }

    private static List<TerrainFace> faces(JsonElement encoded) {
        int[] values = flatValues(encoded, "faces");
        if (values.length % 3 != 0) throw new IllegalArgumentException("faces must be triples");
        List<TerrainFace> result = new ArrayList<>();
        for (int i = 0; i < values.length; i += 3) {
            result.add(new TerrainFace(0, values[i], values[i + 1], values[i + 2]));
        }
        return List.copyOf(result);
    }

    private static int[] flatValues(JsonElement encoded, String name) {
        if (encoded == null || !encoded.isJsonArray()) {
            throw new IllegalArgumentException("Scene geometry tile is missing " + name + " array");
        }
        JsonArray array = encoded.getAsJsonArray();
        int[] result = new int[array.size()];
        for (int i = 0; i < array.size(); i++) result[i] = array.get(i).getAsInt();
        return result;
    }

    private static int requiredInt(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Scene geometry fixture is missing integer field: " + name);
        }
        return value.getAsInt();
    }

    public record TileGeometry(int plane, int x, int y,
                               List<TerrainVertex> vertices, List<TerrainFace> faces) {
        public TileGeometry {
            if (plane < 0 || x < 0 || y < 0) {
                throw new IllegalArgumentException("Scene geometry coordinates cannot be negative");
            }
            vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
            faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
        }

        private boolean matches(TerrainMesh actual) {
            if (!vertices.equals(actual.vertices()) || faces.size() != actual.faces().size()) return false;
            for (int i = 0; i < faces.size(); i++) {
                TerrainFace expected = faces.get(i);
                TerrainFace value = actual.faces().get(i);
                if (expected.a() != value.a() || expected.b() != value.b() || expected.c() != value.c()) {
                    return false;
                }
            }
            return true;
        }

        private String coordinate() {
            return plane + "," + x + "," + y;
        }
    }

    public record Comparison(boolean matches, int differenceCount, List<String> samples) {
        public Comparison {
            samples = List.copyOf(samples == null ? List.of() : samples);
        }
    }
}
