package com.rspsi.editor.render;

import com.rspsi.editor.model.InstanceChunkTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-shaped view of RuneLite {@code Scene#getInstanceTemplateChunks()}.
 *
 * <p>The OSRS scene is 104x104 tiles, or 13x13 chunks, across four planes.
 * Each slot contains the packed template value used by the client, or -1 when
 * absent. This class models only the API data contract; actually projecting
 * rotated instance geometry remains the separate {@code scene.instances}
 * rendering concern.</p>
 */
public final class InstanceTemplateGrid {
    public static final int SCENE_SIZE = 104;
    public static final int CHUNK_SIZE = InstanceChunkTemplate.CHUNK_SIZE;
    public static final int CHUNKS_PER_AXIS = SCENE_SIZE / CHUNK_SIZE;

    private final int planes;
    private final int[] packed;

    private InstanceTemplateGrid(int planes, int[] packed) {
        this.planes = planes;
        this.packed = packed;
    }

    public static InstanceTemplateGrid from(SceneWindow window) {
        Objects.requireNonNull(window, "window");
        return from(window.planes(), window.instanceTemplates());
    }

    public static InstanceTemplateGrid from(int planes, List<InstanceChunkTemplate> templates) {
        if (planes <= 0) {
            throw new IllegalArgumentException("Instance template grid must have at least one plane");
        }
        Objects.requireNonNull(templates, "templates");
        int[] packed = new int[Math.multiplyExact(
                Math.multiplyExact(planes, CHUNKS_PER_AXIS), CHUNKS_PER_AXIS)];
        Arrays.fill(packed, InstanceChunkTemplate.ABSENT);

        for (InstanceChunkTemplate template : templates) {
            Objects.requireNonNull(template, "template");
            if (template.targetPlane() >= planes) {
                throw new IllegalArgumentException("Instance target plane lies outside the scene");
            }
            validateChunk(template.sceneChunkX(), template.sceneChunkY());
            int index = index(template.targetPlane(), template.sceneChunkX(), template.sceneChunkY(), planes);
            if (packed[index] != InstanceChunkTemplate.ABSENT) {
                throw new IllegalArgumentException("Duplicate instance template target slot: "
                        + template.targetPlane() + ":" + template.sceneChunkX() + ":" + template.sceneChunkY());
            }
            packed[index] = template.encode();
        }
        return new InstanceTemplateGrid(planes, packed);
    }

    public int planes() {
        return planes;
    }

    public int chunksPerAxis() {
        return CHUNKS_PER_AXIS;
    }

    public int packedAt(int plane, int chunkX, int chunkY) {
        validate(plane, chunkX, chunkY);
        return packed[index(plane, chunkX, chunkY, planes)];
    }

    public Optional<InstanceChunkTemplate> templateAt(int plane, int chunkX, int chunkY) {
        return InstanceChunkTemplate.decode(packedAt(plane, chunkX, chunkY), plane, chunkX, chunkY);
    }

    /** Returns a defensive client-shaped [plane][x][y] array. */
    public int[][][] toPackedArray() {
        int[][][] result = new int[planes][CHUNKS_PER_AXIS][CHUNKS_PER_AXIS];
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < CHUNKS_PER_AXIS; x++) {
                for (int y = 0; y < CHUNKS_PER_AXIS; y++) {
                    result[plane][x][y] = packedAt(plane, x, y);
                }
            }
        }
        return result;
    }

    private void validate(int plane, int chunkX, int chunkY) {
        if (plane < 0 || plane >= planes) {
            throw new IllegalArgumentException("Instance plane lies outside the scene");
        }
        validateChunk(chunkX, chunkY);
    }

    private static void validateChunk(int chunkX, int chunkY) {
        if (chunkX < 0 || chunkX >= CHUNKS_PER_AXIS
                || chunkY < 0 || chunkY >= CHUNKS_PER_AXIS) {
            throw new IllegalArgumentException("Instance target chunk lies outside the 13x13 OSRS scene");
        }
    }

    private static int index(int plane, int chunkX, int chunkY, int planes) {
        if (plane >= planes) {
            throw new IllegalArgumentException("Instance plane lies outside the scene");
        }
        return (plane * CHUNKS_PER_AXIS + chunkX) * CHUNKS_PER_AXIS + chunkY;
    }
}
