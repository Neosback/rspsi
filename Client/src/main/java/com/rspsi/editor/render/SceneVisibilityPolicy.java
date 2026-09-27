package com.rspsi.editor.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Derived visibility rules applied to an immutable scene packet.
 *
 * <p>Plane visibility mirrors Terraini's {@code VisiblePlaneWindow.maxPlaneExclusive}:
 * when {@code allHeightsVisible} is true every authored plane is rendered; otherwise
 * only planes 0..{@code currentHeight} (inclusive) are shown, matching the in-game
 * camera plane behaviour.</p>
 *
 * <p>{@code showHiddenTiles} corresponds to Terraini's {@code Options.showHiddenTiles}
 * (flag55) — tiles whose underlay colour equals the HIDDEN_TILE sentinel (12345678)
 * are included and drawn with a distinct tint instead of being silently dropped.</p>
 *
 * <p>{@code showEmptyTiles} corresponds to Terraini's {@code Options.showEmptyTiles}
 * (flag56) — tiles with no underlay or overlay at all receive a grey placeholder quad.</p>
 */
public record SceneVisibilityPolicy(
        int currentHeight,
        boolean allHeightsVisible,
        boolean showHiddenTiles,
        boolean showEmptyTiles,
        RoofRemovalState roofRemovalState
) {
    public SceneVisibilityPolicy {
        roofRemovalState = Objects.requireNonNull(roofRemovalState, "roofRemovalState");
        if (currentHeight < 0 || currentHeight > 3) {
            throw new IllegalArgumentException("currentHeight must be between 0 and 3");
        }
    }

    // -------------------------------------------------------------------------
    // Terraini VisiblePlaneWindow.maxPlaneExclusive logic (exact port)
    // -------------------------------------------------------------------------

    /**
     * Mirrors {@code VisiblePlaneWindow.maxPlaneExclusive(int planes, int currentHeight,
     * boolean allHeights)}:
     * <ul>
     *   <li>If {@code allHeightsVisible}: return {@code planes} (show every plane).</li>
     *   <li>Otherwise: clamp {@code currentHeight} to {@code [0, planes-1]} and return
     *       {@code currentHeight + 1} (show planes 0 through currentHeight inclusive).</li>
     * </ul>
     *
     * @param planes total number of planes in the scene (typically 4)
     * @return the exclusive upper bound: planes with index {@code < maxPlaneExclusive} are visible
     */
    public int maxPlaneExclusive(int planes) {
        if (allHeightsVisible) return planes;
        return Math.max(0, Math.min(planes - 1, currentHeight)) + 1;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /** Editor default: all planes visible, hidden/empty tiles off. */
    public static SceneVisibilityPolicy editor() {
        return new SceneVisibilityPolicy(0, true, false, false,
                RoofRemovalState.disabled());
    }

    /** Single-height view (Terraini currentHeight mode, allHeightsVisible=false). */
    public static SceneVisibilityPolicy atHeight(int height) {
        return new SceneVisibilityPolicy(height, false, false, false,
                RoofRemovalState.disabled());
    }

    // -------------------------------------------------------------------------
    // Fluent builders
    // -------------------------------------------------------------------------

    public SceneVisibilityPolicy withCurrentHeight(int height) {
        return new SceneVisibilityPolicy(height, allHeightsVisible, showHiddenTiles,
                showEmptyTiles, roofRemovalState);
    }

    public SceneVisibilityPolicy withAllHeightsVisible(boolean all) {
        return new SceneVisibilityPolicy(currentHeight, all, showHiddenTiles, showEmptyTiles,
                roofRemovalState);
    }

    public SceneVisibilityPolicy withShowHiddenTiles(boolean show) {
        return new SceneVisibilityPolicy(currentHeight, allHeightsVisible, show, showEmptyTiles,
                roofRemovalState);
    }

    public SceneVisibilityPolicy withShowEmptyTiles(boolean show) {
        return new SceneVisibilityPolicy(currentHeight, allHeightsVisible, showHiddenTiles, show,
                roofRemovalState);
    }

    public SceneVisibilityPolicy withRoofRemovalState(RoofRemovalState state) {
        return new SceneVisibilityPolicy(currentHeight, allHeightsVisible, showHiddenTiles,
                showEmptyTiles, Objects.requireNonNull(state, "roofRemovalState"));
    }

    // -------------------------------------------------------------------------
    // Tile gate (context-free; used by editor/debug callers)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this tile's authored plane index is within the
     * {@code maxPlaneExclusive(4)} window computed from the current settings.
     *
     * <p>Bridge decks are authored one plane above where they are drawn
     * (Terraforge {@code GL32ForwardSceneRenderer}: OSRS bridges stored on
     * plane 1 while viewing plane 0 are explicitly re-emitted). A tile whose
     * effective scene plane is below its authored plane is therefore included
     * when its effective plane is visible, even if its authored plane is not.</p>
     *
     * <p>Tile existence is never gated on the hidden/empty-tiles flags here:
     * packet tiles carry authored heights, settings, and objects consumed by
     * scene APIs. Those flags only shape faces downstream in
     * {@link RenderConfig}.</p>
     */
    public boolean includes(SceneTileSnapshot tile) {
        Objects.requireNonNull(tile, "tile");
        int maxPlane = maxPlaneExclusive(4);
        if (tile.authoredPlane() < maxPlane) {
            return true;
        }
        return tile.effectivePlane() < maxPlane && tile.effectivePlane() < tile.authoredPlane();
    }

    // -------------------------------------------------------------------------
    // Packet projection
    // -------------------------------------------------------------------------

    /** Filters a packet without mutating its source tiles or texture repository. */
    public GpuScenePacket apply(GpuScenePacket packet) {
        Objects.requireNonNull(packet, "packet");
        int planes = packet.window().planes();
        int maxPlane = maxPlaneExclusive(planes);
        List<SceneTileSnapshot> visible = packet.tiles().stream()
                .filter(tile -> tile.authoredPlane() < maxPlane
                        || (tile.effectivePlane() < maxPlane
                                && tile.effectivePlane() < tile.authoredPlane()))
                .toList();
        if (visible.equals(packet.tiles())) return packet;
        return new GpuScenePacket(packet.window(), visible, packet.lightingProfile(),
                fingerprint(packet.fingerprint(), visible), packet.textures());
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

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
