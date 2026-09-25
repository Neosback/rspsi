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
        boolean roofsVisible,
        boolean bridgeTilesVisible,
        boolean hiddenTilesVisible,
        boolean collisionVisible,
        boolean wireframe,
        int activePlane,
        SceneVisibilityPolicy.PlaneSelection planeSelection,
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
        planeSelection = Objects.requireNonNull(planeSelection, "plane selection");
        if (activePlane < 0 || activePlane > 3) {
            throw new IllegalArgumentException("Active plane must be between 0 and 3");
        }
        if (!Double.isFinite(brightness) || brightness < 0.0) {
            throw new IllegalArgumentException("Brightness must be finite and non-negative");
        }
        if (!Double.isFinite(exposure)) throw new IllegalArgumentException("Exposure must be finite");
        if (msaaSamples < 0) throw new IllegalArgumentException("MSAA samples cannot be negative");
        if (fogDepthTiles < 0 || fogColor < 0 || fogColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Invalid fog configuration");
        }
        nativeCullingMode = Objects.requireNonNull(nativeCullingMode, "nativeCullingMode");
        gpuDebugView = Objects.requireNonNull(gpuDebugView, "gpuDebugView");
    }

    /** Compatibility constructor from before native culling joined the compiled config. */
    public RenderConfig(RenderProfile profile, boolean terrainVisible, boolean objectsVisible,
                        boolean wallsVisible, boolean wallDecorationsVisible,
                        boolean groundObjectsVisible, boolean groundDecorationsVisible,
                        boolean roofsVisible, boolean bridgeTilesVisible, boolean hiddenTilesVisible,
                        boolean collisionVisible, boolean wireframe, int activePlane,
                        SceneVisibilityPolicy.PlaneSelection planeSelection, double brightness,
                        double exposure, int msaaSamples, int fogDepthTiles, int fogColor,
                        boolean invisibleObjectsVisible, GpuDebugView gpuDebugView) {
        this(profile, terrainVisible, objectsVisible, wallsVisible, wallDecorationsVisible,
                groundObjectsVisible, groundDecorationsVisible, roofsVisible, bridgeTilesVisible,
                hiddenTilesVisible, collisionVisible, wireframe, activePlane, planeSelection,
                brightness, exposure, msaaSamples, fogDepthTiles, fogColor,
                invisibleObjectsVisible, BackfacePolicy.defaultMode(), gpuDebugView);
    }

    /** Compatibility constructor before GPU debug views were part of the frame config. */
    public RenderConfig(RenderProfile profile, boolean terrainVisible, boolean objectsVisible,
                        boolean wallsVisible, boolean wallDecorationsVisible,
                        boolean groundObjectsVisible, boolean groundDecorationsVisible,
                        boolean roofsVisible, boolean bridgeTilesVisible, boolean hiddenTilesVisible,
                        boolean collisionVisible, boolean wireframe, int activePlane,
                        SceneVisibilityPolicy.PlaneSelection planeSelection, double brightness,
                        double exposure, int msaaSamples, int fogDepthTiles, int fogColor,
                        boolean invisibleObjectsVisible) {
        this(profile, terrainVisible, objectsVisible, wallsVisible, wallDecorationsVisible,
                groundObjectsVisible, groundDecorationsVisible, roofsVisible, bridgeTilesVisible,
                hiddenTilesVisible, collisionVisible, wireframe, activePlane, planeSelection,
                brightness, exposure, msaaSamples, fogDepthTiles, fogColor,
                invisibleObjectsVisible, BackfacePolicy.defaultMode(), GpuDebugView.NONE);
    }

    /** Compatibility constructor before fog settings were part of the frame config. */
    public RenderConfig(RenderProfile profile, boolean terrainVisible, boolean objectsVisible,
                        boolean wallsVisible, boolean wallDecorationsVisible,
                        boolean groundObjectsVisible, boolean groundDecorationsVisible,
                        boolean roofsVisible, boolean bridgeTilesVisible, boolean hiddenTilesVisible,
                        boolean collisionVisible, boolean wireframe, int activePlane,
                        SceneVisibilityPolicy.PlaneSelection planeSelection, double brightness,
                        double exposure, int msaaSamples) {
        this(profile, terrainVisible, objectsVisible, wallsVisible, wallDecorationsVisible,
                groundObjectsVisible, groundDecorationsVisible, roofsVisible, bridgeTilesVisible,
                hiddenTilesVisible, collisionVisible, wireframe, activePlane, planeSelection,
                brightness, exposure, msaaSamples, 0, 0x101827, false,
                BackfacePolicy.defaultMode(), GpuDebugView.NONE);
    }

    /** Converts the frame settings into the shared scene projection policy. */
    public SceneVisibilityPolicy visibilityPolicy() {
        SceneVisibilityPolicy policy = switch (planeSelection) {
            case ALL -> SceneVisibilityPolicy.editor();
            case AUTHORED_PLANE -> SceneVisibilityPolicy.authoredPlane(activePlane);
            case EFFECTIVE_PLANE -> SceneVisibilityPolicy.effectivePlane(activePlane);
            case CLIENT_TRAVERSAL -> SceneVisibilityPolicy.clientTraversal(activePlane);
        };
        return policy.withBridgeUpperGeometry(!bridgeTilesVisible)
                .withRoofGeometry(!roofsVisible);
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
        List<SceneTileSnapshot> tiles = visible.tiles().stream()
                .map(this::filterTile)
                .toList();
        if (tiles.equals(visible.tiles())) return visible;
        return new GpuScenePacket(visible.window(), tiles, visible.lightingProfile(),
                fingerprint(visible.fingerprint(), tiles), visible.textures());
    }

    private SceneTileSnapshot filterTile(SceneTileSnapshot tile) {
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
                tile.planeCullLevel(), tile.bridge(), terrainVisible ? tile.terrain()
                        : java.util.Optional.empty(), models, layers,
                objectsVisible ? tile.occluders() : List.of(), tile.roofRelated(), tile.visibleBelow());
    }

    private boolean modelVisible(ModelRenderPacket model) {
        if (!objectsVisible) return false;
        if (model.editorMarker() && !invisibleObjectsVisible) return false;
        if (!roofsVisible && model.roofRelated()) return false;
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

    public static RenderConfig vanillaDefault() {
        return new RenderConfig(RenderProfile.VANILLA_COMPATIBILITY,
                true, true, true, true, true, true, true, true, false,
                false, false, 0, SceneVisibilityPolicy.PlaneSelection.CLIENT_TRAVERSAL,
                1.0, 0.0, 0, 0, 0x101827, false,
                BackfacePolicy.defaultMode(), GpuDebugView.NONE);
    }
}
