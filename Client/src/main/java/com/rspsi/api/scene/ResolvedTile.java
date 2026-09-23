package com.rspsi.api.scene;

import com.rspsi.api.DecorativeObject;
import com.rspsi.api.GameObject;
import com.rspsi.api.GroundObject;
import com.rspsi.api.Point;
import com.rspsi.api.SceneTileModel;
import com.rspsi.api.SceneTilePaint;
import com.rspsi.api.Tile;
import com.rspsi.api.TileObject;
import com.rspsi.api.WallObject;
import com.rspsi.api.coords.LocalPoint;
import com.rspsi.api.coords.WorldPoint;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.SceneObjectIdentity;
import com.rspsi.editor.render.SceneTileSnapshot;
import com.rspsi.editor.render.TerrainRenderPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link Tile} over one {@link SceneTileSnapshot}. */
final class ResolvedTile implements Tile {
    private final SceneView scene;
    private final SceneTileSnapshot snapshot;
    private final Tile bridge;
    private final int sceneX;
    private final int sceneY;
    private final SceneTilePaint paint;
    private final SceneTileModel model;
    private WallObject wall;
    private DecorativeObject decoration;
    private GroundObject ground;
    private final List<GameObject> anchoredGameObjects = new ArrayList<>();
    private final List<GameObject> gameObjects = new ArrayList<>();

    ResolvedTile(SceneView scene, SceneTileSnapshot snapshot, Tile bridge) {
        this.scene = scene;
        this.snapshot = snapshot;
        this.bridge = bridge;
        this.sceneX = snapshot.worldAddress().worldX() - scene.getBaseX();
        this.sceneY = snapshot.worldAddress().worldY() - scene.getBaseY();
        int[] corners = scene.heightsAt(snapshot.renderLevel(), sceneX, sceneY);
        boolean flat = corners[0] == corners[1] && corners[1] == corners[2] && corners[2] == corners[3];
        TerrainRenderPacket terrain = snapshot.terrain().orElse(null);
        if (terrain == null) {
            paint = null;
            model = null;
        } else if (terrain.shape() <= 1) {
            paint = ResolvedTilePaint.of(terrain, flat);
            model = null;
        } else {
            paint = null;
            model = ResolvedTileModel.of(terrain, sceneX, sceneY, flat);
        }
        buildObjects();
    }

    private void buildObjects() {
        Map<String, List<ModelRenderPacket>> packetsByIdentity = new LinkedHashMap<>();
        for (ModelRenderPacket packet : snapshot.models()) {
            SceneObjectIdentity identity = packet.sceneObjectIdentity();
            if (!identity.present()) continue;
            packetsByIdentity.computeIfAbsent(identity.stableId(), key -> new ArrayList<>()).add(packet);
        }
        Optional<TileSnapshot> authored = scene.authoredTile(snapshot.worldAddress());
        if (authored.isEmpty()) {
            // No authored source: fall back to the placements that rendered.
            for (List<ModelRenderPacket> packets : packetsByIdentity.values()) {
                add(ResolvedTileObject.of(this, packets.get(0).sceneObjectIdentity(), packets));
            }
            return;
        }
        // The client keeps every placed loc as a scene object, including
        // invisible blockers and locs whose models are empty or unresolved,
        // so objects come from the authored placements, not from geometry.
        Map<String, Integer> occurrences = new HashMap<>();
        for (WorldObject placed : authored.orElseThrow().objects()) {
            WorldObject world = new WorldObject(placed.id(), placed.type(), placed.rotation(),
                    snapshot.authoredPlane(), snapshot.worldAddress().worldX(),
                    snapshot.worldAddress().worldY());
            String placementKey = world.id() + ":" + world.type() + ":" + world.rotation();
            int occurrence = occurrences.merge(placementKey, 1, Integer::sum) - 1;
            SceneObjectIdentity identity = identity(world, occurrence);
            List<ModelRenderPacket> packets = packetsByIdentity.getOrDefault(identity.stableId(), List.of());
            add(ResolvedTileObject.of(this, identity, packets));
        }
    }

    private SceneObjectIdentity identity(WorldObject world, int occurrence) {
        var definitions = scene.definitions();
        RenderObject footprint = RenderObject.resolve(world,
                definitions.object(world.id()).orElse(null), null,
                definitions.objectCollision(world.id()).orElse(null), null);
        return SceneObjectIdentity.of(world, footprint.footprintWidth(),
                footprint.footprintLength(), occurrence);
    }

    private void add(ResolvedTileObject object) {
        switch (object) {
            case WallObject value -> wall = value;
            case DecorativeObject value -> decoration = value;
            case GroundObject value -> ground = value;
            case GameObject value -> {
                anchoredGameObjects.add(value);
                gameObjects.add(value);
            }
            default -> {
            }
        }
    }

    void addCoveringGameObject(GameObject object) {
        gameObjects.add(object);
    }

    List<GameObject> anchoredGameObjects() {
        return Collections.unmodifiableList(anchoredGameObjects);
    }

    void collectAnchored(List<TileObject> into) {
        if (wall != null) into.add(wall);
        if (decoration != null) into.add(decoration);
        if (ground != null) into.add(ground);
        into.addAll(anchoredGameObjects);
    }

    int sceneX() {
        return sceneX;
    }

    int sceneY() {
        return sceneY;
    }

    SceneView scene() {
        return scene;
    }

    @Override
    public WorldPoint getWorldLocation() {
        return new WorldPoint(snapshot.worldAddress().worldX(), snapshot.worldAddress().worldY(), getPlane());
    }

    @Override
    public LocalPoint getLocalLocation() {
        return LocalPoint.fromScene(sceneX, sceneY);
    }

    @Override
    public Point getSceneLocation() {
        return new Point(sceneX, sceneY);
    }

    @Override public int getPlane() { return snapshot.effectivePlane(); }
    @Override public int getRenderLevel() { return snapshot.renderLevel(); }
    @Override public int getPhysicalLevel() { return snapshot.planeCullLevel(); }
    @Override public int getAuthoredPlane() { return snapshot.authoredPlane(); }
    @Override public int getTileSettings() { return snapshot.tileFlags(); }
    @Override public Tile getBridge() { return bridge; }
    @Override public SceneTilePaint getSceneTilePaint() { return paint; }
    @Override public SceneTileModel getSceneTileModel() { return model; }
    @Override public WallObject getWallObject() { return wall; }
    @Override public DecorativeObject getDecorativeObject() { return decoration; }
    @Override public GroundObject getGroundObject() { return ground; }

    @Override
    public List<GameObject> getGameObjects() {
        return Collections.unmodifiableList(gameObjects);
    }

    @Override
    public String toString() {
        return "Tile" + getWorldLocation() + " authoredPlane=" + getAuthoredPlane();
    }
}
