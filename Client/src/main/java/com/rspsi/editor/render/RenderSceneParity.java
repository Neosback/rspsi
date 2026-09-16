package com.rspsi.editor.render;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares two neutral scene snapshots for future TSPS/RuneLite fixture use.
 * The report is bounded so a broken large region remains useful in a console.
 */
public final class RenderSceneParity {
    private static final int MAX_REPORTED_DIFFERENCES = 128;

    private RenderSceneParity() {
    }

    public static Report compare(RenderScene expected, RenderScene actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        List<Difference> differences = new ArrayList<>();
        int differenceCount = 0;
        if (expected.document().width() != actual.document().width()
                || expected.document().length() != actual.document().length()
                || expected.document().planes() != actual.document().planes()) {
            differenceCount++;
            add(differences, "document", "dimensions", dimensions(expected), dimensions(actual));
        }

        int planes = Math.min(expected.document().planes(), actual.document().planes());
        int width = Math.min(expected.document().width(), actual.document().width());
        int length = Math.min(expected.document().length(), actual.document().length());
        int comparedTiles = planes * width * length;
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    differenceCount += compareValue(differences, "terrain", coordinate,
                            expected.terrainMeshes().get(coordinate), actual.terrainMeshes().get(coordinate));
                    differenceCount += compareValue(differences, "material", coordinate,
                            expected.terrainMaterials().get(coordinate), actual.terrainMaterials().get(coordinate));
                    differenceCount += compareValue(differences, "lighting", coordinate,
                            expected.terrainLighting().get(coordinate), actual.terrainLighting().get(coordinate));
                    differenceCount += compareValue(differences, "collision", coordinate,
                            expected.collision().get(coordinate), actual.collision().get(coordinate));
                }
            }
        }
        differenceCount += compareValue(differences, "objects", null,
                expected.objects(), actual.objects());
        differenceCount += compareValue(differences, "renderObjects", null,
                expected.renderObjects(), actual.renderObjects());
        differenceCount += compareValue(differences, "bridges", null,
                expected.bridges(), actual.bridges());
        return new Report(comparedTiles, differenceCount, differences);
    }

    private static int compareValue(List<Difference> differences, String scope,
                                    TileCoordinate coordinate, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) return 0;
        add(differences, scope, coordinate == null ? "global" : coordinate.toString(), expected, actual);
        return 1;
    }

    private static void add(List<Difference> differences, String scope, String location,
                            Object expected, Object actual) {
        if (differences.size() < MAX_REPORTED_DIFFERENCES) {
            differences.add(new Difference(scope, location, String.valueOf(expected), String.valueOf(actual)));
        }
    }

    private static String dimensions(RenderScene scene) {
        return scene.document().planes() + "x" + scene.document().width()
                + "x" + scene.document().length();
    }

    public record Difference(String scope, String location, String expected, String actual) {
        public Difference {
            scope = Objects.requireNonNull(scope, "scope");
            location = Objects.requireNonNull(location, "location");
            expected = Objects.requireNonNull(expected, "expected");
            actual = Objects.requireNonNull(actual, "actual");
        }
    }

    public record Report(int comparedTiles, int differenceCount, List<Difference> differences) {
        public Report {
            if (comparedTiles < 0 || differenceCount < 0) {
                throw new IllegalArgumentException("Parity counts cannot be negative");
            }
            differences = List.copyOf(Objects.requireNonNull(differences, "differences"));
            if (differences.size() > MAX_REPORTED_DIFFERENCES) {
                throw new IllegalArgumentException("Parity report exceeds its difference limit");
            }
        }

        public boolean matches() {
            return differenceCount == 0;
        }

        public boolean truncated() {
            return differenceCount > differences.size();
        }
    }
}
