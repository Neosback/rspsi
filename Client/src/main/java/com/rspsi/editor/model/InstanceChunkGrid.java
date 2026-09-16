package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Neutral collection of the packed instance templates for one scene.
 *
 * <p>The grid uses the client convention {@code [plane][sceneChunkX][sceneChunkY]}.
 * Missing entries remain holes. A source chunk may occur more than once, so
 * source-to-scene lookup deliberately returns a list.</p>
 */
public final class InstanceChunkGrid {
    private final int sceneBaseX;
    private final int sceneBaseY;
    private final List<InstanceChunkTransform> transforms;

    private InstanceChunkGrid(int sceneBaseX, int sceneBaseY,
                              List<InstanceChunkTransform> transforms) {
        this.sceneBaseX = sceneBaseX;
        this.sceneBaseY = sceneBaseY;
        this.transforms = List.copyOf(transforms);
    }

    /** Decodes a client-style {@code [plane][sceneChunkX][sceneChunkY]} grid. */
    public static InstanceChunkGrid decode(int[][][] packedTemplates, int sceneBaseX, int sceneBaseY) {
        Objects.requireNonNull(packedTemplates, "packedTemplates");
        if (sceneBaseX < 0 || sceneBaseY < 0) {
            throw new IllegalArgumentException("Scene base coordinates cannot be negative");
        }
        List<InstanceChunkTransform> transforms = new ArrayList<>();
        for (int plane = 0; plane < packedTemplates.length; plane++) {
            int[][] row = Objects.requireNonNull(packedTemplates[plane], "packedTemplates[plane]");
            for (int sceneChunkX = 0; sceneChunkX < row.length; sceneChunkX++) {
                int[] column = Objects.requireNonNull(row[sceneChunkX], "packedTemplates[plane][x]");
                for (int sceneChunkY = 0; sceneChunkY < column.length; sceneChunkY++) {
                    InstanceChunkTemplate.decode(column[sceneChunkY], plane, sceneChunkX, sceneChunkY)
                            .map(template -> new InstanceChunkTransform(template, sceneBaseX, sceneBaseY))
                            .ifPresent(transforms::add);
                }
            }
        }
        return new InstanceChunkGrid(sceneBaseX, sceneBaseY, transforms);
    }

    public int sceneBaseX() {
        return sceneBaseX;
    }

    public int sceneBaseY() {
        return sceneBaseY;
    }

    public List<InstanceChunkTransform> transforms() {
        return transforms;
    }

    /** Returns every scene occurrence of a source tile, including duplicates. */
    public List<TileCoordinate> sourceToScene(TileCoordinate source) {
        Objects.requireNonNull(source, "source");
        return transforms.stream()
                .filter(transform -> transform.containsSource(source))
                .map(transform -> transform.sourceToScene(source))
                .toList();
    }

    /** Resolves a scene tile to its source tile, or empty when it is a hole. */
    public Optional<TileCoordinate> sceneToSource(TileCoordinate scene) {
        Objects.requireNonNull(scene, "scene");
        return transforms.stream()
                .filter(transform -> transform.containsScene(scene))
                .findFirst()
                .map(transform -> transform.sceneToSource(scene));
    }
}
