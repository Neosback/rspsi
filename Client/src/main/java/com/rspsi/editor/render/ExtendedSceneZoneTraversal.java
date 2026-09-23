package com.rspsi.editor.render;

import java.util.Objects;
import java.util.Optional;

/**
 * Per-frame top-level extended-scene zone gate.
 *
 * <p>RuneLite's GPU renderer owns a fixed 23x23 root zone grid for the
 * 184x184 extended scene and translates those zone coordinates by the
 * five-zone scene offset when drawing. RSPSi stores the same geometry by
 * absolute {@link WorldZoneCoordinate}; this class performs the inverse
 * projection at frame time without rebasing or rebuilding resident GPU
 * geometry.</p>
 *
 * <p>This contract deliberately does not invent a second camera draw-distance
 * rule. RuneLite's callback traversal and RSPSi's native frustum/clip-space
 * rejection are different frontend mechanisms. The parity requirement here is
 * that only zones belonging to the top-level 184x184 compatibility window are
 * eligible, including its five-zone border, while sub-worldviews retain their
 * ordinary unshifted zone space.</p>
 */
public final class ExtendedSceneZoneTraversal {
    private static final float WORLD_UNITS_PER_TILE = 128.0f;
    private static final float WORLD_UNITS_PER_ZONE =
            WORLD_UNITS_PER_TILE * ExtendedSceneZoneLayout.ZONE_SIZE;

    private final SceneWindow window;
    private final ExtendedSceneZoneLayout layout;

    public ExtendedSceneZoneTraversal(SceneWindow window) {
        this.window = Objects.requireNonNull(window, "window");
        this.layout = window.extendedSceneZoneLayout();
    }

    public SceneWindow window() {
        return window;
    }

    public ExtendedSceneZoneLayout layout() {
        return layout;
    }

    /** Extended root traversal applies only to RuneLite's top-level world view. */
    public boolean applies() {
        return window.worldViewId() == -1;
    }

    /**
     * Returns whether one resident absolute world zone belongs to the frame's
     * scene traversal domain.
     */
    public boolean includes(WorldZoneCoordinate zone) {
        Objects.requireNonNull(zone, "zone");
        if (!applies()) {
            return true;
        }
        return layout.extendedZone(window, zone).isPresent();
    }

    /**
     * Captures the camera's current extended-zone location while preserving the
     * camera-independent resident-zone gate.
     */
    public Frame frame(CameraState camera) {
        Objects.requireNonNull(camera, "camera");
        Optional<ExtendedSceneZoneLayout.Point> cameraZone = applies()
                ? extendedZoneForWorldPosition(camera.x(), camera.z())
                : Optional.empty();
        return new Frame(this, cameraZone);
    }

    Optional<ExtendedSceneZoneLayout.Point> extendedZoneForWorldPosition(float worldX,
                                                                          float worldZ) {
        int zoneX = floorZone(worldX);
        int zoneY = floorZone(worldZ);
        int extendedX = zoneX - (window.sceneBaseX() >> 3) + layout.sceneZoneOffset();
        int extendedY = zoneY - (window.sceneBaseY() >> 3) + layout.sceneZoneOffset();
        return layout.containsExtendedZone(extendedX, extendedY)
                ? Optional.of(new ExtendedSceneZoneLayout.Point(extendedX, extendedY))
                : Optional.empty();
    }

    private static int floorZone(float worldUnits) {
        return (int) Math.floor(worldUnits / WORLD_UNITS_PER_ZONE);
    }

    public record Frame(
            ExtendedSceneZoneTraversal traversal,
            Optional<ExtendedSceneZoneLayout.Point> cameraExtendedZone
    ) {
        public Frame {
            traversal = Objects.requireNonNull(traversal, "traversal");
            cameraExtendedZone = Objects.requireNonNull(cameraExtendedZone, "cameraExtendedZone");
        }

        public boolean includes(WorldZoneCoordinate zone) {
            return traversal.includes(zone);
        }

        public boolean includes(GpuDrawCommand command) {
            Objects.requireNonNull(command, "command");
            return includes(WorldZoneCoordinate.from(command.tile()));
        }

        public boolean cameraInExtendedScene() {
            return !traversal.applies() || cameraExtendedZone.isPresent();
        }

        public boolean cameraInBorder() {
            return cameraExtendedZone
                    .map(point -> traversal.layout().isBorderZone(point.x(), point.y()))
                    .orElse(false);
        }
    }
}
