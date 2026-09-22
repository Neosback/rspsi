package com.rspsi.editor.render;

import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldTileAddress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Assembles the immutable scene-tile order consumed by any renderer backend.
 * Terrain, objects, bridges, and future occluders meet here; no frontend is
 * allowed to rebuild this ordering independently.
 */
public final class GpuScenePacketBuilder {
    /**
     * Builds a packet from the bounded scene produced by the canonical region
     * session. Local document coordinates are projected into the scene-window
     * world origin; no cache or frontend access is introduced here.
     */
    public GpuScenePacket build(SceneWindow window, RenderScene scene) {
        return build(window, scene, SceneVisibilityPolicy.editor());
    }

    /** Builds a packet and applies a renderer/frontend visibility projection. */
    public GpuScenePacket build(SceneWindow window, RenderScene scene,
                                SceneVisibilityPolicy visibility) {
        return visibility.apply(buildUnfiltered(window, scene));
    }

    private GpuScenePacket buildUnfiltered(SceneWindow window, RenderScene scene) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(scene, "scene");
        java.util.Set<WorldTileAddress> addresses = new java.util.LinkedHashSet<>();
        scene.terrainPackets().keySet().forEach(value -> addresses.add(worldAddress(window, value)));
        scene.terrainMeshes().keySet().forEach(value -> addresses.add(worldAddress(window, value)));
        scene.collision().keySet().forEach(value -> addresses.add(worldAddress(window, value)));
        scene.modelPackets().forEach(value -> addresses.add(worldAddress(window, value.anchor())));
        scene.objects().forEach(value -> addresses.add(WorldTileAddress.of(
                window.sceneBaseX() + value.x(), window.sceneBaseY() + value.y(), value.plane())));
        scene.bridges().forEach(value -> {
            addresses.add(worldAddress(window, value.upper()));
            addresses.add(worldAddress(window, value.lower()));
        });
        List<WorldTileAddress> ordered = new ArrayList<>(addresses);
        ordered.sort(Comparator.comparingInt(WorldTileAddress::plane)
                .thenComparingInt(WorldTileAddress::worldX)
                .thenComparingInt(WorldTileAddress::worldY));

        MapByAddress localObjects = new MapByAddress(scene.renderObjects());
        List<SceneTileSnapshot> tiles = new ArrayList<>(ordered.size());
        for (WorldTileAddress address : ordered) {
            TileCoordinate local = new TileCoordinate(address.plane(),
                    address.worldX() - window.sceneBaseX(), address.worldY() - window.sceneBaseY());
            TerrainRenderPacket terrain = scene.terrainPackets().get(local);
            List<ModelRenderPacket> models = scene.modelPackets().stream()
                    .filter(value -> value.anchor().equals(local))
                    .toList();
            Optional<com.rspsi.editor.model.BridgeLink> bridge = scene.bridges().stream()
                    .filter(value -> value.upper().equals(local))
                    .findFirst();
            int flags = scene.document().tile(local.plane(), local.x(), local.y()).snapshot().flags();
            ScenePlaneSemantics planes = ScenePlaneSemantics.resolve(
                    address.plane(), flags, bridge.isPresent());
            List<SceneLayer> layers = layers(terrain, models, scene.textures());
            boolean roofRelated = localObjects.roofRelated(local);
            tiles.add(new SceneTileSnapshot(addressToCoordinate(address), address, flags,
                    planes.scenePlane(), planes.authoredPlane(), planes.renderLevel(),
                    planes.planeCullLevel(), bridge, Optional.ofNullable(terrain), models,
                    layers, List.of(), roofRelated,
                    planes.scenePlane() < planes.authoredPlane()));
        }
        return new GpuScenePacket(window, tiles, scene.lightingProfile(),
                fingerprint(window, tiles, scene.textures()), scene.textures());
    }

    public GpuScenePacket build(SceneWindow window, RenderWindowScene scene) {
        return build(window, scene, SceneVisibilityPolicy.editor());
    }

    /** Builds a world-window packet and applies a renderer/frontend visibility projection. */
    public GpuScenePacket build(SceneWindow window, RenderWindowScene scene,
                                SceneVisibilityPolicy visibility) {
        return visibility.apply(buildUnfiltered(window, scene));
    }

    private GpuScenePacket buildUnfiltered(SceneWindow window, RenderWindowScene scene) {
        return buildWindowPacket(null, window, scene, java.util.Set.of()).packet();
    }

    /**
     * Rebuilds only tiles in dirty absolute world zones while retaining immutable
     * snapshots from the previous unfiltered packet everywhere else.
     */
    public IncrementalBuildResult buildIncremental(GpuScenePacket previous,
                                                   SceneWindow window,
                                                   RenderWindowScene scene,
                                                   java.util.Set<WorldZoneCoordinate> dirtyZones) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(dirtyZones, "dirtyZones");
        if (!sameWindow(previous.window(), window)) {
            GpuScenePacket packet = buildUnfiltered(window, scene);
            return new IncrementalBuildResult(packet, packet.tiles().size(), 0, true);
        }
        return buildWindowPacket(previous, window, scene, dirtyZones);
    }

    private IncrementalBuildResult buildWindowPacket(GpuScenePacket previous,
                                                     SceneWindow window,
                                                     RenderWindowScene scene,
                                                     java.util.Set<WorldZoneCoordinate> dirtyZones) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(scene, "scene");
        java.util.Set<WorldTileAddress> addressSet = new java.util.LinkedHashSet<>(scene.terrainMeshes().keySet());
        addressSet.addAll(scene.terrainPackets().keySet());
        addressSet.addAll(scene.modelPackets().keySet());
        addressSet.addAll(scene.collision().keySet());
        scene.objects().forEach(value -> addressSet.add(value.address()));
        scene.bridges().forEach(value -> {
            addressSet.add(value.authored());
            addressSet.add(value.effective());
        });
        List<WorldTileAddress> addresses = new ArrayList<>(addressSet);
        addresses.sort(Comparator.comparingInt(WorldTileAddress::plane)
                .thenComparingInt(WorldTileAddress::worldX)
                .thenComparingInt(WorldTileAddress::worldY));

        java.util.Map<WorldTileAddress, SceneTileSnapshot> previousTiles =
                new java.util.HashMap<>();
        if (previous != null) {
            previous.tiles().forEach(tile -> previousTiles.put(tile.worldAddress(), tile));
        }

        List<SceneTileSnapshot> tiles = new ArrayList<>(addresses.size());
        int rebuilt = 0;
        int reused = 0;
        for (WorldTileAddress address : addresses) {
            SceneTileSnapshot retained = previousTiles.get(address);
            if (retained != null && !dirtyZones.contains(WorldZoneCoordinate.from(address))) {
                tiles.add(retained);
                reused++;
                continue;
            }
            tiles.add(buildWindowTile(address, scene));
            rebuilt++;
        }
        GpuScenePacket packet = new GpuScenePacket(window, tiles, scene.lightingProfile(),
                fingerprint(window, tiles, scene.textures()), scene.textures());
        return new IncrementalBuildResult(packet, rebuilt, reused, previous == null);
    }

    private static SceneTileSnapshot buildWindowTile(WorldTileAddress address,
                                                     RenderWindowScene scene) {
        Optional<WorldBridgeLink> worldBridge = scene.bridges().stream()
                .filter(value -> value.authored().equals(address))
                .findFirst();
        Optional<BridgeLink> bridge = worldBridge.map(value -> new BridgeLink(
                new TileCoordinate(value.authored().plane(), value.authored().worldX(), value.authored().worldY()),
                new TileCoordinate(value.effective().plane(), value.effective().worldX(), value.effective().worldY())));
        TerrainRenderPacket terrain = scene.terrainPackets().get(address);
        List<ModelRenderPacket> models = scene.modelPackets().getOrDefault(address, List.of());
        List<SceneLayer> layers = layers(terrain, models, scene.textures());
        int tileFlags = scene.tileFlags().getOrDefault(address, 0);
        ScenePlaneSemantics planes = ScenePlaneSemantics.resolve(
                address.plane(), tileFlags, bridge.isPresent());
        List<SceneOccluder> occluders = occluders(
                address, scene, models, planes.scenePlane());
        boolean roofRelated = scene.objects().stream()
                .filter(value -> value.address().equals(address))
                .map(value -> value.object().shape().map(shape -> shape.id() >= 12 && shape.id() <= 21)
                        .orElse(false))
                .anyMatch(Boolean::booleanValue);
        return new SceneTileSnapshot(addressToCoordinate(address), address, tileFlags,
                planes.scenePlane(), planes.authoredPlane(), planes.renderLevel(),
                planes.planeCullLevel(), bridge, Optional.ofNullable(terrain), models,
                layers, occluders, roofRelated,
                planes.scenePlane() < planes.authoredPlane());
    }

    private static boolean sameWindow(SceneWindow first, SceneWindow second) {
        return first.sceneBaseX() == second.sceneBaseX()
                && first.sceneBaseY() == second.sceneBaseY()
                && first.planes() == second.planes()
                && first.border() == second.border()
                && first.minimumRenderLevel() == second.minimumRenderLevel()
                && first.worldViewId() == second.worldViewId()
                && first.sourceRegionIds().equals(second.sourceRegionIds())
                && first.instanceTemplates().equals(second.instanceTemplates());
    }

    public record IncrementalBuildResult(
            GpuScenePacket packet,
            int rebuiltTiles,
            int reusedTiles,
            boolean fullRebuild
    ) {
        public IncrementalBuildResult {
            packet = Objects.requireNonNull(packet, "packet");
            if (rebuiltTiles < 0 || reusedTiles < 0) {
                throw new IllegalArgumentException("Tile counts cannot be negative");
            }
        }
    }

    private static WorldTileAddress worldAddress(SceneWindow window, TileCoordinate coordinate) {
        return WorldTileAddress.of(window.sceneBaseX() + coordinate.x(),
                window.sceneBaseY() + coordinate.y(), coordinate.plane());
    }

    private static final class MapByAddress {
        private final List<RenderObject> renderObjects;

        private MapByAddress(List<RenderObject> renderObjects) {
            this.renderObjects = renderObjects;
        }

        private boolean roofRelated(TileCoordinate coordinate) {
            return renderObjects.stream()
                    .filter(value -> value.object().plane() == coordinate.plane()
                            && value.object().x() == coordinate.x()
                            && value.object().y() == coordinate.y())
                    .anyMatch(value -> value.shape().map(shape -> shape.id() >= 12 && shape.id() <= 21)
                            .orElse(false));
        }
    }

    private static com.rspsi.editor.model.TileCoordinate addressToCoordinate(WorldTileAddress address) {
        return new com.rspsi.editor.model.TileCoordinate(address.plane(), address.worldX(), address.worldY());
    }

    private static String fingerprint(SceneWindow window, List<SceneTileSnapshot> tiles,
                                      java.util.Map<Integer, RenderTextureResource> textures) {
        StringBuilder value = new StringBuilder();
        value.append(window.sceneBaseX()).append(':').append(window.sceneBaseY()).append(':')
                .append(window.planes()).append(':').append(window.border()).append(':')
                .append(window.minimumRenderLevel()).append(':').append(window.worldViewId()).append(':')
                .append(window.instance()).append('|');
        window.sourceRegionIds().stream().sorted()
                .forEach(regionId -> value.append(regionId).append(','));
        value.append(';');
        for (SceneTileSnapshot tile : tiles) {
            value.append(tile.coordinate()).append('|').append(tile.worldAddress()).append('|')
                    .append(tile.tileFlags()).append('|')
                    .append(tile.effectivePlane()).append('|')
                    .append(tile.authoredPlane()).append('|')
                    .append(tile.renderLevel()).append('|')
                    .append(tile.planeCullLevel())
                    .append('|').append(tile.terrain()).append('|').append(tile.models())
                    .append('|').append(tile.layers()).append('|').append(tile.occluders())
                    .append('|').append(tile.bridge()).append('|').append(tile.roofRelated())
                    .append('|').append(tile.visibleBelow()).append(';');
        }
        textures.values().stream().sorted(Comparator.comparingInt(RenderTextureResource::id))
                .forEach(texture -> value.append("texture=").append(texture.id())
                        .append(':').append(texture.pixelStatus())
                        .append(':').append(texture.width()).append('x').append(texture.height())
                        .append(':').append(java.util.Arrays.hashCode(texture.pixels())).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) result.append(String.format("%02x", valueByte & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<SceneLayer> layers(TerrainRenderPacket terrain, List<ModelRenderPacket> models,
                                           java.util.Map<Integer, RenderTextureResource> textures) {
        List<SceneLayer> result = new ArrayList<>();
        if (terrain != null) result.add(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of()));
        addLayer(result, SceneLayer.Kind.WALL, models, ObjectCategory.WALL, textures);
        addLayer(result, SceneLayer.Kind.WALL_DECORATION, models, ObjectCategory.WALL_DECOR, textures);
        addLayer(result, SceneLayer.Kind.GROUND_OBJECT, models, ObjectCategory.GROUND, textures);
        addLayer(result, SceneLayer.Kind.GROUND_DECORATION, models, ObjectCategory.GROUND_DECOR, textures);
        return List.copyOf(result);
    }

    private static void addLayer(List<SceneLayer> layers, SceneLayer.Kind kind,
                                 List<ModelRenderPacket> models, ObjectCategory category,
                                 java.util.Map<Integer, RenderTextureResource> textures) {
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < models.size(); index++) {
            if (models.get(index).category() == category) indices.add(index);
        }
        if (indices.isEmpty()) return;
        List<Integer> opaque = new ArrayList<>();
        List<Integer> transparent = new ArrayList<>();
        for (int index : indices) {
            if (hasTransparentGeometry(models.get(index))) transparent.add(index);
            else opaque.add(index);
        }
        layers.add(new SceneLayer(kind, indices, opaque, transparent));
    }

    /**
     * OSRS model alpha is zero for opaque faces, so any non-zero face alpha is
     * retained as a separate submission class. Render type is deliberately not
     * consulted: the client's Mesh.renderFace uses it purely as a shading
     * selector (0 shaded, 1 flat colour, 2/3 textured), so keying opacity off
     * it drew every flat-textured face at half alpha.
     */
    private static boolean hasTransparentGeometry(ModelRenderPacket model) {
        // isTransparentFace already fully decides model transparency. An
        // extra texture-pixel test would move cutout banners/foliage into the
        // no-depth-write alpha stream. RuneLite keeps those triangles in the
        // opaque stream and discards transparent texels in the fragment
        // shader, preserving depth ownership for the visible texels.
        return model.triangles().stream().anyMatch(GpuScenePacketBuilder::isTransparentFace);
    }

    /** Model alpha selects the alpha stream; texture cutouts stay opaque. */
    private static boolean isTransparentFace(ModelTriangle face) {
        return face.alpha() != 0;
    }

    /** Emits only explicit definition-backed occluders; movement blocking alone is not visual occlusion. */
    private static List<SceneOccluder> occluders(WorldTileAddress address, RenderWindowScene scene,
                                                 List<ModelRenderPacket> models, int scenePlane) {
        List<SceneOccluder> result = new ArrayList<>();
        for (WorldRenderObject worldObject : scene.objects()) {
            if (!worldObject.address().equals(address)) {
                continue;
            }
            List<ModelRenderPacket> objectModels = models.stream()
                    .filter(model -> model.objectId() == worldObject.object().object().id())
                    .toList();
            // An object's footprint is volume geometry, not a fixed type-1 or
            // type-2 occlusion plane. Emitting it as one of those planes makes
            // the resolver ignore one axis and can hide large areas of valid
            // terrain behind multi-tile objects. Only model-clipped wall
            // edges below become occluders until volumetric occlusion has a
            // separately verified contract.
            if (worldObject.object().appearance().modelClipped()) {
                for (WallOccluder wall : clippedWallOccluders(worldObject.object().object())) {
                    int height = edgeHeight(scene, address, wall);
                    result.add(new SceneOccluder(
                            wall.type(), wall.minTileX(address), wall.maxTileX(address),
                            wall.minTileY(address), wall.maxTileY(address),
                            scenePlane, scenePlane,
                            wall.minWorldX(address), wall.maxWorldX(address),
                            wall.minWorldY(address), wall.maxWorldY(address),
                            height - 240, height));
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Mirrors RuneLite's model-clipped wall mask creation for the static wall
     * shapes whose cache definitions contribute type-1/type-2 occluders.
     * Region-wide mask merging is intentionally left to a later visibility
     * pass; these inputs preserve the exact wall edges and heights first.
     */
    private static List<WallOccluder> clippedWallOccluders(com.rspsi.editor.model.WorldObject object) {
        if (object.type() == 0) {
            return wallForOrientation(object.wallOrientationA()).stream().toList();
        }
        if (object.type() != 2) return List.of();
        List<WallOccluder> result = new ArrayList<>();
        result.addAll(wallForOrientation(object.wallOrientationA()));
        result.addAll(wallForOrientation(object.wallOrientationB()));
        return List.copyOf(result);
    }

    /** Converts RuneLite's orientation bitfield into a fixed wall edge. */
    private static List<WallOccluder> wallForOrientation(int orientation) {
        return switch (orientation) {
            case 1 -> List.of(wall(1, 0, 0)); // west
            case 2 -> List.of(wall(2, 0, 1)); // north
            case 4 -> List.of(wall(1, 1, 0)); // east
            case 8 -> List.of(wall(2, 0, 0)); // south
            // Diagonal wall orientations are not valid fixed-axis occluder
            // planes; they remain visible geometry but are intentionally not
            // emitted into this conservative occlusion pass.
            default -> List.of();
        };
    }

    private static WallOccluder wall(int type, int xOffset, int yOffset) {
        return new WallOccluder(type, xOffset, yOffset);
    }

    private static int edgeHeight(RenderWindowScene scene, WorldTileAddress address,
                                  WallOccluder wall) {
        TerrainRenderPacket packet = scene.terrainPackets().get(address);
        if (packet == null) return 0;
        int edge = wall.type() == 1 ? wall.xOffset() == 0 ? 0 : 128
                : wall.yOffset() == 0 ? 0 : 128;
        int total = 0;
        int count = 0;
        for (TerrainRenderVertex vertex : packet.vertices()) {
            if ((wall.type() == 1 && vertex.x() == edge)
                    || (wall.type() == 2 && vertex.y() == edge)) {
                total += vertex.height();
                count++;
            }
        }
        return count == 0 ? 0 : total / count;
    }

    private record WallOccluder(int type, int xOffset, int yOffset) {
        private int minTileX(WorldTileAddress address) {
            return address.regionLocalX() + xOffset;
        }

        private int maxTileX(WorldTileAddress address) {
            return minTileX(address);
        }

        private int minTileY(WorldTileAddress address) {
            return address.regionLocalY() + yOffset;
        }

        private int maxTileY(WorldTileAddress address) {
            return minTileY(address);
        }

        private int minWorldX(WorldTileAddress address) {
            return address.worldX() * 128 + (type == 1 ? xOffset * 128 : 0);
        }

        private int maxWorldX(WorldTileAddress address) {
            return type == 1 ? minWorldX(address) : address.worldX() * 128 + 128;
        }

        private int minWorldY(WorldTileAddress address) {
            return address.worldY() * 128 + (type == 2 ? yOffset * 128 : 0);
        }

        private int maxWorldY(WorldTileAddress address) {
            return type == 2 ? minWorldY(address) : address.worldY() * 128 + 128;
        }
    }
}
