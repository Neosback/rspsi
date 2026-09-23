package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Translucent footprint marker for a placed loc with no drawable geometry
 * (invisible blockers, authored-empty models, no model for the shape, or a
 * missing definition). Editor presentation only; see
 * {@link ScenePresentation#EDITOR}.
 *
 * <p>Vertices use the model-packet frame: X/Z from the anchor tile's
 * south-west corner in local units (128 per tile), Y model-local with
 * negative up. Walls become panels on the edges their
 * {@link WorldObject#wallOrientationA()}/{@code B} bits name.</p>
 */
final class EditorGhostMarker {
    /** Packed HSL magenta, clearly not a cache colour. */
    private static final int COLOUR = (53 << 10) | (7 << 7) | 72;
    private static final int WALL_HEIGHT = 240;
    private static final int THICKNESS = 16;

    private EditorGhostMarker() {
    }

    static ModelRenderPacket build(WorldObject object, DefinitionProvider definitions,
                                   int occurrence, int placementHeight) {
        RenderObject footprint = RenderObject.resolve(object,
                definitions.object(object.id()).orElse(null), null,
                definitions.objectCollision(object.id()).orElse(null), null);
        int width = footprint.footprintWidth() * 128;
        int length = footprint.footprintLength() * 128;

        List<ModelVertex> vertices = new ArrayList<>();
        List<ModelTriangle> triangles = new ArrayList<>();
        ObjectCategory category = object.category();
        switch (category) {
            case WALL -> {
                int edges = object.wallOrientationA() | object.wallOrientationB();
                if ((edges & 1) != 0) box(vertices, triangles, 0, 0, THICKNESS, 128, WALL_HEIGHT);
                if ((edges & 2) != 0) box(vertices, triangles, 0, 128 - THICKNESS, 128, 128, WALL_HEIGHT);
                if ((edges & 4) != 0) box(vertices, triangles, 128 - THICKNESS, 0, 128, 128, WALL_HEIGHT);
                if ((edges & 8) != 0) box(vertices, triangles, 0, 0, 128, THICKNESS, WALL_HEIGHT);
                if ((edges & 16) != 0) box(vertices, triangles, 0, 128 - 32, 32, 128, WALL_HEIGHT);
                if ((edges & 32) != 0) box(vertices, triangles, 128 - 32, 128 - 32, 128, 128, WALL_HEIGHT);
                if ((edges & 64) != 0) box(vertices, triangles, 128 - 32, 0, 128, 32, WALL_HEIGHT);
                if ((edges & 128) != 0) box(vertices, triangles, 0, 0, 32, 32, WALL_HEIGHT);
                if (edges == 0) box(vertices, triangles, 48, 48, 80, 80, WALL_HEIGHT);
            }
            case WALL_DECOR -> box(vertices, triangles, 44, 44, 84, 84, 64);
            case GROUND_DECOR -> box(vertices, triangles, 16, 16, 112, 112, 8);
            case GROUND, UNKNOWN -> box(vertices, triangles, 8, 8, width - 8, length - 8, 128);
        }

        int[] bounds = bounds(vertices);
        return new ModelRenderPacket(
                new TileCoordinate(object.plane(), object.x(), object.y()), object.id(), category,
                vertices, triangles, List.of(), -1,
                bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                false, false, placementHeight, false)
                .withSceneObjectIdentity(SceneObjectIdentity.of(object,
                        footprint.footprintWidth(), footprint.footprintLength(), occurrence))
                .asEditorGhost(ModelPacketBuilder.GHOST_TRANSPARENCY);
    }

    /** Axis-aligned box from (x0,z0) to (x1,z1), standing {@code height} units up. */
    private static void box(List<ModelVertex> vertices, List<ModelTriangle> triangles,
                            int x0, int z0, int x1, int z1, int height) {
        int base = vertices.size();
        int top = -height;
        int[][] corners = {
                {x0, 0, z0}, {x1, 0, z0}, {x1, 0, z1}, {x0, 0, z1},
                {x0, top, z0}, {x1, top, z0}, {x1, top, z1}, {x0, top, z1}};
        for (int[] corner : corners) {
            vertices.add(new ModelVertex(corner[0], corner[1], corner[2], 0, -1, 0, 0.0f, 0.0f));
        }
        int[][] faces = {
                {4, 5, 6}, {4, 6, 7},
                {0, 1, 5}, {0, 5, 4},
                {1, 2, 6}, {1, 6, 5},
                {2, 3, 7}, {2, 7, 6},
                {3, 0, 4}, {3, 4, 7}};
        for (int[] face : faces) {
            triangles.add(new ModelTriangle(base + face[0], base + face[1], base + face[2],
                    COLOUR, COLOUR, COLOUR, -1, 0, 0, 0));
        }
    }

    private static int[] bounds(List<ModelVertex> vertices) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (ModelVertex vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }
}
