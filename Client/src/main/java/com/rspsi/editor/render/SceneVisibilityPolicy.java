package com.rspsi.editor.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Derived visibility rules applied to an immutable scene packet.
 *
 * <p>This is deliberately separate from {@link com.rspsi.editor.model.WorldDocument}.
 * The authored map keeps every plane, bridge, and roof relationship; a
 * frontend chooses which of those derived projections to submit. That lets
 * the editor's all-planes view and a client-like single-plane view share the
 * same packet and the same renderer backends.</p>
 */
public record SceneVisibilityPolicy(
        PlaneSelection planeSelection,
        int selectedPlane,
        boolean hideBridgeUpperGeometry,
        boolean hideRoofGeometry
) {
    public enum PlaneSelection {
        ALL,
        AUTHORED_PLANE,
        EFFECTIVE_PLANE,
        CLIENT_TRAVERSAL
    }

    public SceneVisibilityPolicy {
        planeSelection = Objects.requireNonNull(planeSelection, "planeSelection");
        if (selectedPlane < 0 || selectedPlane > 3) {
            throw new IllegalArgumentException("Selected plane must be between 0 and 3");
        }
    }

    /** Editor default: preserve every loaded plane and diagnostic surface. */
    public static SceneVisibilityPolicy editor() {
        return new SceneVisibilityPolicy(PlaneSelection.ALL, 0, false, false);
    }

    /** Client-like projection keyed by the tile's authored scene plane. */
    public static SceneVisibilityPolicy authoredPlane(int plane) {
        return new SceneVisibilityPolicy(PlaneSelection.AUTHORED_PLANE, plane, false, false);
    }

    /** Diagnostic projection keyed by bridge-resolved current scene plane. */
    public static SceneVisibilityPolicy effectivePlane(int plane) {
        return new SceneVisibilityPolicy(PlaneSelection.EFFECTIVE_PLANE, plane, false, false);
    }

    /**
     * Client traversal projection. The selected plane is the active client
     * scene/camera plane; tiles from any current scene plane may participate
     * when their physical/cull level is at or below it.
     */
    public static SceneVisibilityPolicy clientTraversal(int activePlane) {
        return new SceneVisibilityPolicy(PlaneSelection.CLIENT_TRAVERSAL, activePlane, false, false);
    }

    public SceneVisibilityPolicy withBridgeUpperGeometry(boolean hidden) {
        return new SceneVisibilityPolicy(planeSelection, selectedPlane, hidden, hideRoofGeometry);
    }

    public SceneVisibilityPolicy withRoofGeometry(boolean hidden) {
        return new SceneVisibilityPolicy(planeSelection, selectedPlane, hideBridgeUpperGeometry, hidden);
    }

    /** Returns whether one immutable tile projection should be submitted. */
    public boolean includes(SceneTileSnapshot tile) {
        Objects.requireNonNull(tile, "tile");
        if (planeSelection == PlaneSelection.AUTHORED_PLANE
                && tile.authoredPlane() != selectedPlane) {
            return false;
        }
        if (planeSelection == PlaneSelection.EFFECTIVE_PLANE
                && tile.effectivePlane() != selectedPlane) {
            return false;
        }
        if (planeSelection == PlaneSelection.CLIENT_TRAVERSAL
                && tile.planeCullLevel() > selectedPlane) {
            return false;
        }
        if (hideBridgeUpperGeometry && tile.visibleBelow()) {
            return false;
        }
        return !hideRoofGeometry || !tile.roofRelated();
    }

    /** Filters a packet without mutating its source tiles or texture repository. */
    public GpuScenePacket apply(GpuScenePacket packet) {
        Objects.requireNonNull(packet, "packet");
        // Roof visibility is a model-level projection. A roof lives on the
        // same tile as the terrain and walls below it; dropping the complete
        // tile creates the characteristic holes seen around castle roofs and
        // bridge approaches.
        SceneContract contract = packet.window().contract();
        List<SceneTileSnapshot> visible = packet.tiles().stream()
                .filter(tile -> includesSceneMinimum(contract, tile))
                .filter(this::includesPlaneAndBridge)
                .map(this::filterRoofGeometry)
                .toList();
        if (visible.equals(packet.tiles())) return packet;
        return new GpuScenePacket(packet.window(), visible, packet.lightingProfile(),
                fingerprint(packet.fingerprint(), visible), packet.textures());
    }

    private boolean includesSceneMinimum(SceneContract contract, SceneTileSnapshot tile) {
        // ALL is the editor/debug projection and intentionally retains every
        // loaded plane. Client-like plane projections mirror Scene.minPlane
        // by rejecting current scene planes below the scene minimum first.
        return planeSelection == PlaneSelection.ALL
                || contract.rendersScenePlane(tile.effectivePlane());
    }

    private boolean includesPlaneAndBridge(SceneTileSnapshot tile) {
        if (planeSelection == PlaneSelection.AUTHORED_PLANE
                && tile.authoredPlane() != selectedPlane) {
            return false;
        }
        if (planeSelection == PlaneSelection.EFFECTIVE_PLANE
                && tile.effectivePlane() != selectedPlane) {
            return false;
        }
        if (planeSelection == PlaneSelection.CLIENT_TRAVERSAL
                && tile.planeCullLevel() > selectedPlane) {
            return false;
        }
        return !hideBridgeUpperGeometry || !tile.visibleBelow();
    }

    private SceneTileSnapshot filterRoofGeometry(SceneTileSnapshot tile) {
        if (!hideRoofGeometry || !tile.roofRelated()) return tile;

        java.util.Map<Integer, Integer> remapped = new java.util.HashMap<>();
        List<ModelRenderPacket> models = new java.util.ArrayList<>();
        for (int index = 0; index < tile.models().size(); index++) {
            ModelRenderPacket model = tile.models().get(index);
            if (model.roofRelated()) continue;
            remapped.put(index, models.size());
            models.add(model);
        }

        List<SceneLayer> layers = new java.util.ArrayList<>();
        for (SceneLayer layer : tile.layers()) {
            if (layer.kind() == SceneLayer.Kind.TERRAIN) {
                layers.add(layer);
                continue;
            }
            List<Integer> all = remap(layer.modelIndices(), remapped);
            if (!all.isEmpty()) {
                layers.add(new SceneLayer(layer.kind(), all,
                        remap(layer.opaqueModelIndices(), remapped),
                        remap(layer.transparentModelIndices(), remapped)));
            }
        }
        return new SceneTileSnapshot(tile.coordinate(), tile.worldAddress(), tile.tileFlags(),
                tile.effectivePlane(), tile.authoredPlane(), tile.renderLevel(),
                tile.planeCullLevel(), tile.bridge(), tile.terrain(), models, layers,
                tile.occluders(), false, tile.visibleBelow());
    }

    private static List<Integer> remap(List<Integer> source,
                                       java.util.Map<Integer, Integer> remapped) {
        return source.stream().filter(remapped::containsKey).map(remapped::get).toList();
    }

    private String fingerprint(String packetFingerprint, List<SceneTileSnapshot> visible) {
        StringBuilder value = new StringBuilder(packetFingerprint)
                .append("|visibility=").append(this);
        visible.forEach(tile -> value.append('|').append(tile.worldAddress())
                .append(':').append(tile.effectivePlane())
                .append(':').append(tile.authoredPlane())
                .append(':').append(tile.renderLevel())
                .append(':').append(tile.planeCullLevel()));
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
}
