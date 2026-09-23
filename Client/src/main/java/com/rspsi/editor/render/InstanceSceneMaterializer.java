package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.map.OsrsRegionDecoder;
import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.TerrainHeightSource;
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
 * derived document in scene-local coordinates before the ordinary render
 * pipeline sees the scene. Terrain height origins follow the same load order
 * as the vendored client, while authored/unknown decoded heights are rotated
 * as an explicit corner lattice so editor-created terrain still projects
 * predictably.</p>
 */
public final class InstanceSceneMaterializer {
    private static final int SCENE_SIZE = InstanceTemplateGrid.SCENE_SIZE;
    private static final int HEIGHT_GRID_SIZE = SCENE_SIZE + 1;

    private final DefinitionProvider definitions;

    public InstanceSceneMaterializer(DefinitionProvider definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public WorldDocument materialize(SceneWindow window) {
        Objects.requireNonNull(window, "window");
        if (!window.instance()) {
            throw new IllegalArgumentException("Instance materialization requires instance templates");
        }

        InstanceTemplateGrid grid = window.instanceTemplateGrid();
        WorldRegionWindow source = window.sourceRegions().copy();
        source.stitchSharedEdges();
        WorldDocument target = new WorldDocument(SCENE_SIZE, SCENE_SIZE, window.planes());
        int[][][] heights = new int[window.planes()][HEIGHT_GRID_SIZE][HEIGHT_GRID_SIZE];

        // RuneLite's instance loader walks target planes/chunks in this order.
        // Missing terrain is filled immediately so later chunks observe the
        // same west/south boundary values as the client.
        for (int plane = 0; plane < grid.planes(); plane++) {
            for (int chunkX = 0; chunkX < grid.chunksPerAxis(); chunkX++) {
                for (int chunkY = 0; chunkY < grid.chunksPerAxis(); chunkY++) {
                    InstanceChunkTemplate template =
                            grid.templateAt(plane, chunkX, chunkY).orElse(null);
                    if (template != null && source.tile(
                            template.sourcePlane(),
                            template.sourceOriginX(),
                            template.sourceOriginY()).isPresent()) {
                        copyTerrainChunk(source, target, heights, template);
                    } else {
                        fillMissingChunkHeights(
                                heights[plane],
                                chunkX * InstanceChunkTemplate.CHUNK_SIZE,
                                chunkY * InstanceChunkTemplate.CHUNK_SIZE);
                    }
                }
            }
        }

        // The client performs a second plane-zero boundary pass only for
        // genuinely absent template slots. It copies all four available
        // neighbouring edges into the empty 8x8 area to avoid hard seams.
        for (int chunkX = 0; chunkX < grid.chunksPerAxis(); chunkX++) {
            for (int chunkY = 0; chunkY < grid.chunksPerAxis(); chunkY++) {
                if (grid.templateAt(0, chunkX, chunkY).isEmpty()) {
                    fillPlaneZeroMissingBoundaries(
                            heights[0],
                            chunkX * InstanceChunkTemplate.CHUNK_SIZE,
                            chunkY * InstanceChunkTemplate.CHUNK_SIZE);
                }
            }
        }

        publishHeightCorners(target, heights);

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
                                         int[][][] heights,
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
                        .restore(copyTerrainMetadata(snapshot, template.rotation()));

                if (snapshot.heightSource().cacheEncoded()) {
                    heights[template.targetPlane()][targetX][targetY] =
                            replayCacheHeight(snapshot.heightSource(), heights,
                                    template, projected, targetX, targetY);
                } else {
                    projectDecodedCorners(snapshot, heights[template.targetPlane()],
                            template.sceneChunkX() * InstanceChunkTemplate.CHUNK_SIZE,
                            template.sceneChunkY() * InstanceChunkTemplate.CHUNK_SIZE,
                            sourceX, sourceY, template.rotation());
                }
            }
        }
    }

    /**
     * Replays the raw client height opcode semantics on the target plane.
     *
     * <p>For higher target planes the source absolute height is deliberately
     * ignored: opcode 0 means previous target plane minus 240, while explicit
     * opcode 1 stores a delta from the previous target plane. This is exactly
     * what {@code class264.loadTerrain} does while loading an instance.</p>
     */
    private static int replayCacheHeight(TerrainHeightSource source,
                                         int[][][] heights,
                                         InstanceChunkTemplate template,
                                         TileOffset projected,
                                         int targetX,
                                         int targetY) {
        int plane = template.targetPlane();
        if (plane == 0) {
            if (source.generated()) {
                return OsrsRegionDecoder.generatedHeightAtWorldNoiseCoordinate(
                        template.sourceOriginX() + projected.x(),
                        template.sourceOriginY() + projected.y());
            }
            return -source.explicitValue() * 8;
        }
        int below = heights[plane - 1][targetX][targetY];
        return source.generated() ? below - 240 : below - source.explicitValue() * 8;
    }

    /**
     * Projects editor-authored or legacy unknown decoded heights as a coherent
     * 9x9 corner lattice. Cache-decoded terrain takes the raw replay path above.
     */
    private static void projectDecodedCorners(TileSnapshot source,
                                              int[][] targetHeights,
                                              int targetOriginX,
                                              int targetOriginY,
                                              int sourceX,
                                              int sourceY,
                                              int rotation) {
        putRotatedCorner(targetHeights, targetOriginX, targetOriginY,
                sourceX, sourceY, rotation, source.southWestHeight());
        putRotatedCorner(targetHeights, targetOriginX, targetOriginY,
                sourceX + 1, sourceY, rotation, source.southEastHeight());
        putRotatedCorner(targetHeights, targetOriginX, targetOriginY,
                sourceX + 1, sourceY + 1, rotation, source.northEastHeight());
        putRotatedCorner(targetHeights, targetOriginX, targetOriginY,
                sourceX, sourceY + 1, rotation, source.northWestHeight());
    }

    private static void putRotatedCorner(int[][] heights,
                                         int targetOriginX,
                                         int targetOriginY,
                                         int x,
                                         int y,
                                         int rotation,
                                         int height) {
        CornerOffset point = rotateCorner(x, y, rotation);
        heights[targetOriginX + point.x()][targetOriginY + point.y()] = height;
    }

    private static CornerOffset rotateCorner(int x, int y, int rotation) {
        int edge = InstanceChunkTemplate.CHUNK_SIZE;
        return switch (rotation & 3) {
            case 0 -> new CornerOffset(x, y);
            case 1 -> new CornerOffset(y, edge - x);
            case 2 -> new CornerOffset(edge - x, edge - y);
            case 3 -> new CornerOffset(edge - y, x);
            default -> throw new AssertionError();
        };
    }

    /** Mirrors vendored-client {@code class226.method5057}. */
    private static void fillMissingChunkHeights(int[][] heights, int originX, int originY) {
        for (int x = 0; x < InstanceChunkTemplate.CHUNK_SIZE; x++) {
            for (int y = 0; y < InstanceChunkTemplate.CHUNK_SIZE; y++) {
                heights[originX + x][originY + y] = 0;
            }
        }

        if (originX > 0) {
            for (int offset = 1; offset < InstanceChunkTemplate.CHUNK_SIZE; offset++) {
                heights[originX][originY + offset] =
                        heights[originX - 1][originY + offset];
            }
        }
        if (originY > 0) {
            for (int offset = 1; offset < InstanceChunkTemplate.CHUNK_SIZE; offset++) {
                heights[originX + offset][originY] =
                        heights[originX + offset][originY - 1];
            }
        }

        if (originX > 0 && heights[originX - 1][originY] != 0) {
            heights[originX][originY] = heights[originX - 1][originY];
        } else if (originY > 0 && heights[originX][originY - 1] != 0) {
            heights[originX][originY] = heights[originX][originY - 1];
        } else if (originX > 0 && originY > 0
                && heights[originX - 1][originY - 1] != 0) {
            heights[originX][originY] = heights[originX - 1][originY - 1];
        }
    }

    /** Mirrors the height portion of vendored-client {@code ScriptFrame.method749}. */
    private static void fillPlaneZeroMissingBoundaries(int[][] heights,
                                                       int originX,
                                                       int originY) {
        int maxSceneIndex = SCENE_SIZE - 1;
        for (int y = originY; y <= originY + InstanceChunkTemplate.CHUNK_SIZE; y++) {
            for (int x = originX; x <= originX + InstanceChunkTemplate.CHUNK_SIZE; x++) {
                if (x < 0 || x >= SCENE_SIZE || y < 0 || y >= SCENE_SIZE) {
                    continue;
                }
                if (x == originX && x > 0) {
                    heights[x][y] = heights[x - 1][y];
                }
                if (x == originX + InstanceChunkTemplate.CHUNK_SIZE
                        && x < maxSceneIndex) {
                    heights[x][y] = heights[x + 1][y];
                }
                if (y == originY && y > 0) {
                    heights[x][y] = heights[x][y - 1];
                }
                if (y == originY + InstanceChunkTemplate.CHUNK_SIZE
                        && y < maxSceneIndex) {
                    heights[x][y] = heights[x][y + 1];
                }
            }
        }
    }

    private static void publishHeightCorners(WorldDocument target, int[][][] heights) {
        for (int plane = 0; plane < target.planes(); plane++) {
            for (int x = 0; x < target.width(); x++) {
                for (int y = 0; y < target.length(); y++) {
                    TileSnapshot current = target.tile(plane, x, y).snapshot();
                    target.tile(plane, x, y).restore(new TileSnapshot(
                            heights[plane][x][y],
                            heights[plane][x + 1][y],
                            heights[plane][x + 1][y + 1],
                            heights[plane][x][y + 1],
                            current.underlayId(),
                            current.overlayId(),
                            current.overlayShape(),
                            current.overlayRotation(),
                            current.flags(),
                            current.objects(),
                            current.heightSource()));
                }
            }
        }
    }

    private static TileSnapshot copyTerrainMetadata(TileSnapshot source, int rotation) {
        return new TileSnapshot(
                0, 0, 0, 0,
                source.underlayId(), source.overlayId(), source.overlayShape(),
                (source.overlayRotation() + rotation) & 3,
                source.flags(), List.of(), source.heightSource());
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

    /** Mirrors vendored-client {@code FontName.method11264} plus its Y counterpart. */
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

    /** Mirrors the footprint-aware coordinate pair in vendored-client {@code Tiles.method2092}. */
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

    record TileOffset(int x, int y) {
    }

    private record CornerOffset(int x, int y) {
    }

    private record Dimensions(int width, int length) {
    }
}
