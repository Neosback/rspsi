package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldRegionWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reconstructs the 104x104 authored scene presented by OSRS instance templates.
 *
 * <p>Source regions remain immutable editor/cache state. This class creates a
 * derived document in scene-local coordinates, rotating terrain corners,
 * overlay orientation and object placement before the ordinary render
 * pipeline sees the scene. That lets terrain compilation, contouring,
 * collision, picking and GPU upload reuse the same code paths as a normal
 * scene instead of teaching every renderer about instance templates.</p>
 */
public final class InstanceSceneMaterializer {
    private final DefinitionProvider definitions;

    public InstanceSceneMaterializer(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public WorldDocument materialize(SceneWindow window) {
        Objects.requireNonNull(window, "window");
        if (!window.instance()) {
            throw new IllegalArgumentException("Instance materialization requires instance templates");
        }

        // Building the grid validates the client-shaped target slots before
        // any derived scene data is written.
        InstanceTemplateGrid grid = window.instanceTemplateGrid();
        WorldRegionWindow source = window.sourceRegions().copy();
        source.stitchSharedEdges();
        WorldDocument target = new WorldDocument(
                InstanceTemplateGrid.SCENE_SIZE,
                InstanceTemplateGrid.SCENE_SIZE,
                window.planes());

        for (int plane = 0; plane < grid.planes(); plane++) {
            for (int chunkX = 0; chunkX < grid.chunksPerAxis(); chunkX++) {
                for (int chunkY = 0; chunkY < grid.chunksPerAxis(); chunkY++) {
                    grid.templateAt(plane, chunkX, chunkY)
                            .ifPresent(template -> copyTerrainChunk(source, target, template));
                }
            }
        }

        // Objects are projected separately because rotating a multi-tile
        // footprint changes its anchor. The target anchor is not necessarily
        // the tile produced by rotating the source anchor as a 1x1 point.
        for (int plane = 0; plane < grid.planes(); plane++) {
            for (int chunkX = 0; chunkX < grid.chunksPerAxis(); chunkX++) {
                for (int chunkY = 0; chunkY < grid.chunksPerAxis(); chunkY++) {
                    grid.templateAt(plane, chunkX, chunkY)
                            .ifPresent(template -> copyObjectsChunk(source, target, template));
                }
            }
        }
        return target;
    }

    private static void copyTerrainChunk(WorldRegionWindow source,
                                         WorldDocument target,
                                         InstanceChunkTemplate template) {
        for (int sourceX = 0; sourceX < InstanceChunkTemplate.CHUNK_SIZE; sourceX++) {
            for (int sourceY = 0; sourceY < InstanceChunkTemplate.CHUNK_SIZE; sourceY++) {
                int worldX = template.sourceOriginX() + sourceX;
                int worldY = template.sourceOriginY() + sourceY;
                TileSnapshot snapshot = source.tile(template.sourcePlane(), worldX, worldY)
                        .orElse(null);
                if (snapshot == null) {
                    continue;
                }

                TileOffset projected = rotateTile(sourceX, sourceY, template.rotation());
                int targetX = template.sceneChunkX() * InstanceChunkTemplate.CHUNK_SIZE
                        + projected.x();
                int targetY = template.sceneChunkY() * InstanceChunkTemplate.CHUNK_SIZE
                        + projected.y();
                target.tile(template.targetPlane(), targetX, targetY)
                        .restore(rotateTerrain(snapshot, template.rotation()));
            }
        }
    }

    private void copyObjectsChunk(WorldRegionWindow source,
                                  WorldDocument target,
                                  InstanceChunkTemplate template) {
        for (int sourceX = 0; sourceX < InstanceChunkTemplate.CHUNK_SIZE; sourceX++) {
            for (int sourceY = 0; sourceY < InstanceChunkTemplate.CHUNK_SIZE; sourceY++) {
                int worldX = template.sourceOriginX() + sourceX;
                int worldY = template.sourceOriginY() + sourceY;
                TileSnapshot snapshot = source.tile(template.sourcePlane(), worldX, worldY)
                        .orElse(null);
                if (snapshot == null || snapshot.objects().isEmpty()) {
                    continue;
                }

                int localRegionX = worldX & 63;
                int localRegionY = worldY & 63;
                for (WorldObject object : snapshot.objects()) {
                    // Region documents retain object anchors as 0..63 local
                    // coordinates. Ignore any non-anchor duplicate defensively.
                    if (object.x() != localRegionX || object.y() != localRegionY
                            || object.plane() != template.sourcePlane()) {
                        continue;
                    }

                    Dimensions size = orientedSize(object);
                    TileOffset projected = rotateObjectAnchor(
                            sourceX, sourceY, template.rotation(), size.width(), size.length());
                    int targetX = template.sceneChunkX() * InstanceChunkTemplate.CHUNK_SIZE
                            + projected.x();
                    int targetY = template.sceneChunkY() * InstanceChunkTemplate.CHUNK_SIZE
                            + projected.y();
                    if (!target.contains(template.targetPlane(), targetX, targetY)) {
                        continue;
                    }

                    WorldObject projectedObject = new WorldObject(
                            object.id(), object.type(),
                            (object.rotation() + template.rotation()) & 3,
                            template.targetPlane(), targetX, targetY);
                    appendObject(target, projectedObject);
                }
            }
        }
    }

    private Dimensions orientedSize(WorldObject object) {
        ObjectDefinitionView definition = definitions.object(object.id()).orElse(null);
        ObjectCollisionView collision = definitions.objectCollision(object.id()).orElse(null);
        int width = Math.max(1, definition != null
                ? definition.width()
                : collision != null ? collision.width() : 1);
        int length = Math.max(1, definition != null
                ? definition.length()
                : collision != null ? collision.length() : 1);
        if ((object.rotation() & 1) != 0) {
            int swap = width;
            width = length;
            length = swap;
        }
        return new Dimensions(width, length);
    }

    private static void appendObject(WorldDocument target, WorldObject object) {
        TileSnapshot current = target.tile(object.plane(), object.x(), object.y()).snapshot();
        List<WorldObject> objects = new ArrayList<>(current.objects());
        objects.add(object);
        target.tile(object.plane(), object.x(), object.y()).restore(new TileSnapshot(
                current.southWestHeight(),
                current.southEastHeight(),
                current.northEastHeight(),
                current.northWestHeight(),
                current.underlayId(),
                current.overlayId(),
                current.overlayShape(),
                current.overlayRotation(),
                current.flags(),
                objects,
                current.heightSource()));
    }

    static TileOffset rotateTile(int x, int y, int rotation) {
        int max = InstanceChunkTemplate.CHUNK_SIZE - 1;
        return switch (rotation & 3) {
            case 0 -> new TileOffset(x, y);
            case 1 -> new TileOffset(y, max - x);
            case 2 -> new TileOffset(max - x, max - y);
            case 3 -> new TileOffset(max - y, x);
            default -> throw new AssertionError();
        };
    }

    static TileOffset rotateObjectAnchor(int x, int y, int rotation,
                                         int width, int length) {
        if (width <= 0 || length <= 0) {
            throw new IllegalArgumentException("Instance object footprint must be positive");
        }
        int max = InstanceChunkTemplate.CHUNK_SIZE - 1;
        return switch (rotation & 3) {
            case 0 -> new TileOffset(x, y);
            case 1 -> new TileOffset(y, max - x - (width - 1));
            case 2 -> new TileOffset(max - x - (width - 1),
                    max - y - (length - 1));
            case 3 -> new TileOffset(max - y - (length - 1), x);
            default -> throw new AssertionError();
        };
    }

    private static TileSnapshot rotateTerrain(TileSnapshot source, int rotation) {
        int sw;
        int se;
        int ne;
        int nw;
        switch (rotation & 3) {
            case 0 -> {
                sw = source.southWestHeight();
                se = source.southEastHeight();
                ne = source.northEastHeight();
                nw = source.northWestHeight();
            }
            case 1 -> {
                sw = source.southEastHeight();
                se = source.northEastHeight();
                ne = source.northWestHeight();
                nw = source.southWestHeight();
            }
            case 2 -> {
                sw = source.northEastHeight();
                se = source.northWestHeight();
                ne = source.southWestHeight();
                nw = source.southEastHeight();
            }
            case 3 -> {
                sw = source.northWestHeight();
                se = source.southWestHeight();
                ne = source.southEastHeight();
                nw = source.northEastHeight();
            }
            default -> throw new AssertionError();
        }
        return new TileSnapshot(
                sw, se, ne, nw,
                source.underlayId(), source.overlayId(), source.overlayShape(),
                (source.overlayRotation() + rotation) & 3,
                source.flags(), List.of(), source.heightSource());
    }

    record TileOffset(int x, int y) {
    }

    private record Dimensions(int width, int length) {
    }
}
