package com.rspsi.editor.render;

import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rspsi.editor.model.ObjectCategory;

/** Immutable renderer configuration compiled once from typed settings. */
public record RenderConfig(
        RenderProfile profile,
        boolean terrainVisible,
        boolean objectsVisible,
        boolean wallsVisible,
        boolean wallDecorationsVisible,
        boolean groundObjectsVisible,
        boolean groundDecorationsVisible,
        boolean bridgeTilesVisible,
        boolean hiddenTilesVisible,
        boolean emptyTilesVisible,
        boolean collisionVisible,
        boolean wireframe,
        int currentHeight,
        boolean allHeightsVisible,
        double brightness,
        double exposure,
        int msaaSamples,
        int fogDepthTiles,
        int fogColor,
        boolean invisibleObjectsVisible,
        BackfacePolicy.NativeCullingMode nativeCullingMode,
        GpuDebugView gpuDebugView
) {
    public RenderConfig {
        profile = Objects.requireNonNull(profile, "render profile");
        nativeCullingMode = Objects.requireNonNull(nativeCullingMode, "nativeCullingMode");
        gpuDebugView = Objects.requireNonNull(gpuDebugView, "gpuDebugView");
        if (currentHeight < 0 || currentHeight > 3) {
            throw new IllegalArgumentException("currentHeight must be between 0 and 3");
        }
        if (!Double.isFinite(brightness) || brightness < 0.0) {
            throw new IllegalArgumentException("Brightness must be finite and non-negative");
        }
        if (!Double.isFinite(exposure)) throw new IllegalArgumentException("Exposure must be finite");
        if (msaaSamples < 0) throw new IllegalArgumentException("MSAA samples cannot be negative");
        if (fogDepthTiles < 0 || fogColor < 0 || fogColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Invalid fog configuration");
        }
    }

    /**
     * Converts the frame settings into the shared scene projection policy.
     *
     * <p>Directly mirrors Terraini's {@code VisiblePlaneWindow.maxPlaneExclusive} contract:
     * {@code allHeightsVisible=true} → show all planes; otherwise show only 0..{@code currentHeight}.</p>
     */
    public SceneVisibilityPolicy visibilityPolicy() {
        return new SceneVisibilityPolicy(
                currentHeight,
                allHeightsVisible,
                hiddenTilesVisible,
                emptyTilesVisible,
                RoofRemovalState.disabled());
    }

    /**
     * Applies frame-time RuneLite roof-removal inputs to the compiled scene policy.
     * The transient state is never persisted into authored map data or settings.
     */
    public SceneVisibilityPolicy visibilityPolicy(RoofRemovalState roofRemovalState) {
        return visibilityPolicy().withRoofRemovalState(
                Objects.requireNonNull(roofRemovalState, "roofRemovalState"));
    }

    /** Presentation-only exposure; authored scene colors remain unchanged. */
    public RenderPresentation presentation() {
        return new RenderPresentation(brightness, exposure, wireframe,
                profile == RenderProfile.VANILLA_COMPATIBILITY,
                fogDepthTiles, fogColor, gpuDebugView);
    }

    /**
     * Applies renderer category settings to one immutable packet. This keeps
     * category visibility out of OpenGL and guarantees the CPU and native
     * backends receive the same semantic submission.
     */
    public GpuScenePacket apply(GpuScenePacket packet) {
        return apply(packet, RoofRemovalState.disabled());
    }

    /**
     * Applies renderer settings plus frame-time roof-removal state through the
     * same neutral packet path consumed by every backend.
     */
    public GpuScenePacket apply(GpuScenePacket packet, RoofRemovalState roofRemovalState) {
        Objects.requireNonNull(packet, "GPU packet");
        GpuScenePacket visible = visibilityPolicy(roofRemovalState).apply(packet);
        // Unchanged tiles keep their instance: incremental GPU plans reuse a tile's
        // fragment by identity, so a fresh copy would re-flatten it on every refresh.
        List<SceneTileSnapshot> tiles = visible.tiles().stream()
                .map(tile -> {
                    SceneTileSnapshot filtered = filterTile(tile);
                    return filtered.equals(tile) ? tile : filtered;
                })
                .toList();
        if (tiles.equals(visible.tiles())) return visible;
        return new GpuScenePacket(visible.window(), tiles, visible.lightingProfile(),
                fingerprint(visible.fingerprint(), tiles), visible.textures());
    }

    private SceneTileSnapshot filterTile(SceneTileSnapshot tile) {
        // Bridge decks are drawn below their authored plane (effective < authored).
        // When bridge tiles are off this matches ScenePlaneFilter.hideBridgeUpperGeometry:
        // the deck draws nothing instead of leaking plane-1 geometry into a plane-0 view.
        if (!bridgeTilesVisible && tile.effectivePlane() < tile.authoredPlane()) {
            return new SceneTileSnapshot(tile.coordinate(), tile.worldAddress(), tile.tileFlags(),
                    tile.effectivePlane(), tile.authoredPlane(), tile.renderLevel(),
                    tile.planeCullLevel(), tile.bridge(), java.util.Optional.empty(), List.of(),
                    List.of(), List.of(), tile.roofRelated(), tile.visibleBelow());
        }
        java.util.Optional<TerrainRenderPacket> terrain =
                terrainVisible ? tile.terrain() : java.util.Optional.empty();
        if (terrain.isPresent()) {
            TerrainRenderPacket packet = terrain.get();
            if (packet.isEmptyPlaceholder() && !emptyTilesVisible) {
                terrain = java.util.Optional.of(withoutFaces(packet));
            } else if (packet.isHiddenPlaceholder() && !hiddenTilesVisible) {
                terrain = java.util.Optional.of(withoutFaces(packet));
            } else if (hiddenTilesVisible && packet.overlayHidden()
                    && packet.underlayHsl() >= 0 && !packet.faces().isEmpty()) {
                // Hidden marker over real ground: retint the whole tile vivid
                // magenta so flagged tiles read at a glance (Terraini software
                // fuchsia parity). Unlit flat highlight stays visible in shadow.
                terrain = java.util.Optional.of(tintedHighlight(packet));
            }
        }
        Map<Integer, Integer> remapped = new HashMap<>();
        List<ModelRenderPacket> models = new ArrayList<>();
        for (int index = 0; index < tile.models().size(); index++) {
            ModelRenderPacket model = tile.models().get(index);
            if (!modelVisible(model)) continue;
            remapped.put(index, models.size());
            models.add(model);
        }
        List<SceneLayer> layers = new ArrayList<>();
        for (SceneLayer layer : tile.layers()) {
            if (!layerVisible(layer.kind())) continue;
            if (layer.kind() == SceneLayer.Kind.TERRAIN) {
                layers.add(new SceneLayer(SceneLayer.Kind.TERRAIN, List.of()));
                continue;
            }
            List<Integer> all = remap(layer.modelIndices(), remapped);
            List<Integer> opaque = remap(layer.opaqueModelIndices(), remapped);
            List<Integer> transparent = remap(layer.transparentModelIndices(), remapped);
            if (!all.isEmpty()) layers.add(new SceneLayer(layer.kind(), all, opaque, transparent));
        }
        return new SceneTileSnapshot(tile.coordinate(), tile.worldAddress(), tile.tileFlags(),
                tile.effectivePlane(), tile.authoredPlane(), tile.renderLevel(),
                tile.planeCullLevel(), tile.bridge(), terrain, models, layers,
                objectsVisible ? tile.occluders() : List.of(), tile.roofRelated(), tile.visibleBelow());
    }

    /** Returns the packet with all faces removed; unreferenced vertices never upload. */
    private static TerrainRenderPacket withoutFaces(TerrainRenderPacket packet) {
        if (packet.faces().isEmpty()) return packet;
        return new TerrainRenderPacket(packet.coordinate(), List.of(), List.of(),
                packet.shape(), packet.rotation(), packet.textureId(),
                packet.underlayHsl(), packet.overlayHsl(), packet.flat(),
                packet.overlayHidden(), packet.overlayMinimapHsl());
    }

    /** Returns the packet with every vertex retinted vivid unlit magenta. */
    private static TerrainRenderPacket tintedHighlight(TerrainRenderPacket packet) {
        List<TerrainRenderVertex> vertices = new ArrayList<>(packet.vertices().size());
        for (TerrainRenderVertex vertex : packet.vertices()) {
            vertices.add(new TerrainRenderVertex(vertex.x(), vertex.y(), vertex.height(),
                    OsrsTerrainColorMath.HIDDEN_HIGHLIGHT_HSL, vertex.u(), vertex.v(),
                    vertex.normalX(), vertex.normalY(), vertex.normalZ(),
                    vertex.normalMagnitude()));
        }
        return new TerrainRenderPacket(packet.coordinate(), vertices, packet.faces(),
                packet.shape(), packet.rotation(), packet.textureId(),
                packet.underlayHsl(), packet.overlayHsl(), packet.flat(),
                packet.overlayHidden(), packet.overlayMinimapHsl());
    }

    private boolean modelVisible(ModelRenderPacket model) {
        if (!objectsVisible) return false;
        if (model.editorMarker() && !invisibleObjectsVisible) return false;
        // Shape 9 diagonal walls live in the client's game-object slot (scene layer), but
        // to an editor they are walls: the Walls toggle hides them too.
        if (model.sceneObjectIdentity().present() && model.sceneObjectIdentity().shape() == 9) {
            return wallsVisible;
        }
        return switch (model.category()) {
            case WALL -> wallsVisible;
            case WALL_DECOR -> wallDecorationsVisible;
            case GROUND -> groundObjectsVisible;
            case GROUND_DECOR -> groundDecorationsVisible;
            case UNKNOWN -> true;
        };
    }

    private boolean layerVisible(SceneLayer.Kind kind) {
        return switch (kind) {
            case TERRAIN -> terrainVisible;
            case WALL -> objectsVisible && wallsVisible;
            case WALL_DECORATION -> objectsVisible && wallDecorationsVisible;
            case GROUND_OBJECT -> objectsVisible && groundObjectsVisible;
            case GROUND_DECORATION -> objectsVisible && groundDecorationsVisible;
        };
    }

    private static List<Integer> remap(List<Integer> source, Map<Integer, Integer> remapped) {
        return source.stream().filter(remapped::containsKey).map(remapped::get).toList();
    }

    private String fingerprint(String packetFingerprint, List<SceneTileSnapshot> tiles) {
        StringBuilder value = new StringBuilder(packetFingerprint).append("|renderConfig=").append(this);
        tiles.forEach(tile -> value.append('|').append(tile.worldAddress()).append(':')
                .append(tile.terrain().isPresent()).append(':').append(tile.models().size())
                .append(':').append(tile.layers()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * The part of this config that shapes GPU plan geometry: which layers and objects are
     * built, including the empty-tile placeholder quads and hidden-tile magenta
     * retints. Planes and bridges are left in (drawn or skipped per frame by
     * {@link #visibilityPolicy()}), and presentation fields (brightness, fog, MSAA, wireframe,
     * culling, debug view) are normalized, so changing them never rebuilds a plan.
     * Toggling the hidden/empty-tiles flags rebuilds the plan once; plane and
     * bridge changes stay frame-time.
     */
    public RenderConfig forPlan() {
        RenderConfig d = vanillaDefault();
        return new RenderConfig(d.profile(), terrainVisible, objectsVisible, wallsVisible,
                wallDecorationsVisible, groundObjectsVisible, groundDecorationsVisible,
                true, hiddenTilesVisible, emptyTilesVisible, d.collisionVisible(),
                d.wireframe(), 0, true, d.brightness(), d.exposure(), d.msaaSamples(),
                d.fogDepthTiles(), d.fogColor(), invisibleObjectsVisible, d.nativeCullingMode(),
                d.gpuDebugView());
    }

    /** Frame-time plane selection for a plan built from {@link #forPlan()}. */
    public ScenePlaneFilter planeFilter() {
        return new ScenePlaneFilter(currentHeight, allHeightsVisible, !bridgeTilesVisible);
    }

    public static RenderConfig vanillaDefault() {
        return new RenderConfig(RenderProfile.VANILLA_COMPATIBILITY,
                true, true, true, true, true, true, true, false, false,
                false, false, 0, true,
                1.0, 0.0, 0, 0, 0x101827, false,
                BackfacePolicy.defaultMode(), GpuDebugView.NONE);
    }
}
