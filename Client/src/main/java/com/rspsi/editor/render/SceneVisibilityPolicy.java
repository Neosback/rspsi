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
        EFFECTIVE_PLANE
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

    /** Client-like projection keyed by bridge-resolved effective plane. */
    public static SceneVisibilityPolicy effectivePlane(int plane) {
        return new SceneVisibilityPolicy(PlaneSelection.EFFECTIVE_PLANE, plane, false, false);
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
                && tile.coordinate().plane() != selectedPlane) {
            return false;
        }
        if (planeSelection == PlaneSelection.EFFECTIVE_PLANE
                && tile.effectivePlane() != selectedPlane) {
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
        List<SceneTileSnapshot> visible = packet.tiles().stream().filter(this::includes).toList();
        if (visible.size() == packet.tiles().size()) return packet;
        return new GpuScenePacket(packet.window(), visible, packet.lightingProfile(),
                fingerprint(packet.fingerprint(), visible), packet.textures());
    }

    private String fingerprint(String packetFingerprint, List<SceneTileSnapshot> visible) {
        StringBuilder value = new StringBuilder(packetFingerprint)
                .append("|visibility=").append(this);
        visible.forEach(tile -> value.append('|').append(tile.worldAddress())
                .append(':').append(tile.effectivePlane()));
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
