package com.rspsi.editor.plugin;

import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.TerrainLight;
import com.rspsi.editor.render.TerrainMaterial;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.Optional;

/**
 * Immutable, frontend-neutral scene view exposed to plugins.
 *
 * <p>The renderer-facing {@link RenderScene} intentionally retains its
 * document reference for efficient internal rebuilding. This type copies the
 * authored tile snapshots and exposes only immutable derived values, so a
 * plugin cannot mutate the session by reaching through a scene callback.</p>
 */
public final class EditorSceneSnapshot {
    private final int width;
    private final int length;
    private final int planes;
    private final WorldWindow window;
    private final Map<TileCoordinate, TileSnapshot> tiles;
    private final Map<TileCoordinate, TerrainMesh> terrainMeshes;
    private final Map<TileCoordinate, TerrainMaterial> terrainMaterials;
    private final Map<TileCoordinate, TerrainAppearance> terrainAppearances;
    private final Map<TileCoordinate, TerrainLight> terrainLighting;
    private final LightingProfile lightingProfile;
    private final Map<TileCoordinate, CollisionTileSnapshot> collision;
    private final Map<TileCoordinate, EditorSceneTileProjection> tileProjections;
    private final List<WorldObject> objects;
    private final List<RenderObject> renderObjects;
    private final List<BridgeLink> bridges;

    private EditorSceneSnapshot(int width, int length, int planes, WorldWindow window,
                                Map<TileCoordinate, TileSnapshot> tiles,
                                Map<TileCoordinate, TerrainMesh> terrainMeshes,
                                Map<TileCoordinate, TerrainMaterial> terrainMaterials,
                                Map<TileCoordinate, TerrainAppearance> terrainAppearances,
                                Map<TileCoordinate, TerrainLight> terrainLighting,
                                LightingProfile lightingProfile,
                                Map<TileCoordinate, CollisionTileSnapshot> collision,
                                Map<TileCoordinate, EditorSceneTileProjection> tileProjections,
                                List<WorldObject> objects,
                                List<RenderObject> renderObjects,
                                List<BridgeLink> bridges) {
        this.width = width;
        this.length = length;
        this.planes = planes;
        this.window = Objects.requireNonNull(window, "window");
        this.tiles = immutableMap(tiles);
        this.terrainMeshes = immutableMap(terrainMeshes);
        this.terrainMaterials = immutableMap(terrainMaterials);
        this.terrainAppearances = immutableMap(terrainAppearances);
        this.terrainLighting = immutableMap(terrainLighting);
        this.lightingProfile = Objects.requireNonNull(lightingProfile, "lightingProfile");
        this.collision = immutableMap(collision);
        this.tileProjections = immutableMap(tileProjections);
        this.objects = List.copyOf(objects);
        this.renderObjects = List.copyOf(renderObjects);
        this.bridges = List.copyOf(bridges);
    }

    /** Copies the authored and derived values needed by tooling consumers. */
    public static EditorSceneSnapshot from(RenderScene scene) {
        Objects.requireNonNull(scene, "scene");
        var document = scene.document();
        return from(scene, new WorldWindow(0, 0, document.width(), document.length()));
    }

    /** Copies a scene while preserving its absolute placement in the OSRS world. */
    public static EditorSceneSnapshot from(RenderScene scene, WorldWindow window) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(window, "window");
        var document = scene.document();
        if (document.width() != window.width() || document.length() != window.length()) {
            throw new IllegalArgumentException("Scene/window dimensions differ");
        }
        Map<TileCoordinate, TileSnapshot> tiles = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    tiles.put(coordinate, document.tile(coordinate).snapshot());
                }
            }
        }
        Map<TileCoordinate, List<RenderObject>> objectsByTile = new HashMap<>();
        for (RenderObject object : scene.renderObjects()) {
            TileCoordinate coordinate = new TileCoordinate(object.object().plane(),
                    object.object().x(), object.object().y());
            objectsByTile.computeIfAbsent(coordinate, ignored -> new ArrayList<>()).add(object);
        }
        // List.sort is stable: preserve the source/game-object-array order
        // within a category while making the semantic scene layers explicit.
        Comparator<RenderObject> sceneLayerOrder = Comparator
                .comparingInt((RenderObject object) -> object.category().layerId());
        for (List<RenderObject> objects : objectsByTile.values()) {
            objects.sort(sceneLayerOrder);
        }

        Map<TileCoordinate, BridgeLink> bridgesByUpper = new HashMap<>();
        for (BridgeLink bridge : scene.bridges()) bridgesByUpper.put(bridge.upper(), bridge);
        Map<TileCoordinate, EditorSceneTileProjection> projections = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    int effectivePlane = effectivePlane(document.planes(), document.width(),
                            document.length(), tiles, coordinate);
                    projections.put(coordinate, new EditorSceneTileProjection(
                            coordinate,
                            tiles.get(coordinate),
                            effectivePlane,
                            Optional.ofNullable(bridgesByUpper.get(coordinate)),
                            Optional.ofNullable(scene.terrainMeshes().get(coordinate)),
                            Optional.ofNullable(scene.terrainMaterials().get(coordinate)),
                            Optional.ofNullable(scene.terrainAppearances().get(coordinate)),
                            Optional.ofNullable(scene.terrainLighting().get(coordinate)),
                            Optional.ofNullable(scene.collision().get(coordinate)),
                            objectsByTile.getOrDefault(coordinate, List.of())));
                }
            }
        }
        return new EditorSceneSnapshot(document.width(), document.length(), document.planes(), window,
                tiles, scene.terrainMeshes(), scene.terrainMaterials(), scene.terrainAppearances(),
                scene.terrainLighting(), scene.lightingProfile(),
                scene.collision(), projections, scene.objects(), scene.renderObjects(), scene.bridges());
    }

    public int width() { return width; }
    public int length() { return length; }
    public int planes() { return planes; }
    public WorldWindow window() { return window; }

    public WorldTile worldTile(TileCoordinate local) {
        return window.toWorld(LocalTile.from(Objects.requireNonNull(local, "local")));
    }

    public boolean contains(TileCoordinate coordinate) {
        return coordinate != null && tiles.containsKey(coordinate);
    }

    public Optional<TileSnapshot> tile(TileCoordinate coordinate) {
        return coordinate == null ? Optional.empty() : Optional.ofNullable(tiles.get(coordinate));
    }

    public Map<TileCoordinate, TileSnapshot> tiles() { return tiles; }
    public Map<TileCoordinate, TerrainMesh> terrainMeshes() { return terrainMeshes; }
    public Map<TileCoordinate, TerrainMaterial> terrainMaterials() { return terrainMaterials; }
    public Map<TileCoordinate, TerrainAppearance> terrainAppearances() { return terrainAppearances; }
    public Map<TileCoordinate, TerrainLight> terrainLighting() { return terrainLighting; }
    public LightingProfile lightingProfile() { return lightingProfile; }
    public Map<TileCoordinate, CollisionTileSnapshot> collision() { return collision; }
    public Map<TileCoordinate, EditorSceneTileProjection> tileProjections() { return tileProjections; }
    public List<WorldObject> objects() { return objects; }
    public List<RenderObject> renderObjects() { return renderObjects; }
    public List<BridgeLink> bridges() { return bridges; }

    /** Applies the canonical authored-to-effective plane rule without a document handle. */
    public int effectivePlane(int authoredPlane, int x, int y) {
        return effectivePlane(planes, width, length, tiles,
                new TileCoordinate(authoredPlane, x, y));
    }

    public Optional<EditorSceneTileProjection> tileProjection(TileCoordinate coordinate) {
        return coordinate == null ? Optional.empty() : Optional.ofNullable(tileProjections.get(coordinate));
    }

    private static int effectivePlane(int planes, int width, int length,
                                      Map<TileCoordinate, TileSnapshot> tiles,
                                      TileCoordinate coordinate) {
        if (coordinate.plane() < 0 || coordinate.plane() >= planes
                || coordinate.x() < 0 || coordinate.x() >= width
                || coordinate.y() < 0 || coordinate.y() >= length) {
            throw new IndexOutOfBoundsException("Tile outside scene: " + coordinate);
        }
        TileSnapshot bridgeTile = planes > 1
                ? tiles.get(new TileCoordinate(1, coordinate.x(), coordinate.y())) : null;
        return bridgeTile != null && OsrsTileFlags.hasBridge(bridgeTile.flags())
                ? coordinate.plane() - 1 : coordinate.plane();
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }
}
