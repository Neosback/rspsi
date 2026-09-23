package com.rspsi.editor.render;

import java.util.Objects;
import java.util.Optional;

/**
 * RuneLite GPU extended-scene zone projection over RSPSi's absolute 8x8 world zones.
 *
 * <p>RuneLite exposes a 184x184 extended top-level scene, partitioned into
 * 23x23 zones of 8x8 tiles. The normal 104x104 scene occupies 13x13 zones
 * centered at a five-zone (40-tile) offset. RuneLite's GPU plugin keeps those
 * modified/extended zone coordinates for upload and subtracts the five-zone
 * offset when converting a top-level zone back into ordinary scene-local
 * coordinates.</p>
 *
 * <p>RSPSi keeps geometry resident by absolute {@link WorldZoneCoordinate},
 * which avoids rebasing GPU allocations whenever the scene moves. This type is
 * the lossless compatibility projection between the two coordinate systems.</p>
 */
public record ExtendedSceneZoneLayout(ExtendedSceneLayout tiles) {
    public static final int ZONE_SIZE = 8;

    public ExtendedSceneZoneLayout {
        tiles = Objects.requireNonNull(tiles, "tiles");
        if ((tiles.sceneSize() % ZONE_SIZE) != 0
                || (tiles.extendedSceneSize() % ZONE_SIZE) != 0
                || (tiles.offset() % ZONE_SIZE) != 0) {
            throw new IllegalArgumentException(
                    "Scene, extended scene, and offset must align to 8x8 zones");
        }
    }

    public static ExtendedSceneZoneLayout runeLite() {
        return new ExtendedSceneZoneLayout(ExtendedSceneLayout.runeLite());
    }

    public int sceneZoneCount() {
        return tiles.sceneSize() / ZONE_SIZE;
    }

    public int extendedZoneCount() {
        return tiles.extendedSceneSize() / ZONE_SIZE;
    }

    public int sceneZoneOffset() {
        return tiles.offset() / ZONE_SIZE;
    }

    public boolean containsSceneZone(int zoneX, int zoneY) {
        return zoneX >= 0 && zoneX < sceneZoneCount()
                && zoneY >= 0 && zoneY < sceneZoneCount();
    }

    public boolean containsExtendedZone(int zoneX, int zoneY) {
        return zoneX >= 0 && zoneX < extendedZoneCount()
                && zoneY >= 0 && zoneY < extendedZoneCount();
    }

    public Point toExtendedZone(int sceneZoneX, int sceneZoneY) {
        if (!containsSceneZone(sceneZoneX, sceneZoneY)) {
            throw new IllegalArgumentException("Scene zone lies outside the 13x13 OSRS scene");
        }
        return new Point(sceneZoneX + sceneZoneOffset(), sceneZoneY + sceneZoneOffset());
    }

    public Optional<Point> toSceneZone(int extendedZoneX, int extendedZoneY) {
        if (!containsExtendedZone(extendedZoneX, extendedZoneY)) {
            return Optional.empty();
        }
        int x = extendedZoneX - sceneZoneOffset();
        int y = extendedZoneY - sceneZoneOffset();
        if (!containsSceneZone(x, y)) {
            return Optional.empty();
        }
        return Optional.of(new Point(x, y));
    }

    public boolean isBorderZone(int extendedZoneX, int extendedZoneY) {
        return containsExtendedZone(extendedZoneX, extendedZoneY)
                && toSceneZone(extendedZoneX, extendedZoneY).isEmpty();
    }

    /**
     * RuneLite GPU's zone-space origin for a top-level scene.
     *
     * <p>This is the exact {@code zx - (SCENE_OFFSET >> 3)} translation used
     * by {@code GpuPlugin.drawZoneOpaque/drawZoneAlpha}. Sub-worldviews do not
     * apply the top-level extended-scene offset.</p>
     */
    public Point renderZone(int extendedZoneX, int extendedZoneY, boolean topLevel) {
        if (!containsExtendedZone(extendedZoneX, extendedZoneY)) {
            throw new IllegalArgumentException("Extended zone lies outside the 23x23 scene");
        }
        int offset = topLevel ? sceneZoneOffset() : 0;
        return new Point(extendedZoneX - offset, extendedZoneY - offset);
    }

    /**
     * Projects one extended top-level zone onto the absolute world-zone
     * identity used by RSPSi's persistent GPU residency cache.
     */
    public WorldZoneCoordinate worldZone(SceneWindow window, int plane,
                                         int extendedZoneX, int extendedZoneY) {
        Objects.requireNonNull(window, "window");
        if (plane < 0 || plane >= window.planes()) {
            throw new IllegalArgumentException("Plane lies outside the scene");
        }
        if ((window.sceneBaseX() & (ZONE_SIZE - 1)) != 0
                || (window.sceneBaseY() & (ZONE_SIZE - 1)) != 0) {
            throw new IllegalArgumentException("Scene base must align to 8x8 OSRS zones");
        }

        Point local = renderZone(extendedZoneX, extendedZoneY, true);
        return new WorldZoneCoordinate(
                plane,
                (window.sceneBaseX() >> 3) + local.x(),
                (window.sceneBaseY() >> 3) + local.y());
    }

    /**
     * Converts an absolute resident world zone back into RuneLite's extended
     * top-level zone coordinates when that zone lies inside the 184x184
     * compatibility window.
     */
    public Optional<Point> extendedZone(SceneWindow window, WorldZoneCoordinate zone) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(zone, "zone");
        if (zone.plane() >= window.planes()) {
            return Optional.empty();
        }
        if ((window.sceneBaseX() & (ZONE_SIZE - 1)) != 0
                || (window.sceneBaseY() & (ZONE_SIZE - 1)) != 0) {
            throw new IllegalArgumentException("Scene base must align to 8x8 OSRS zones");
        }

        int x = zone.zoneX() - (window.sceneBaseX() >> 3) + sceneZoneOffset();
        int y = zone.zoneY() - (window.sceneBaseY() >> 3) + sceneZoneOffset();
        return containsExtendedZone(x, y) ? Optional.of(new Point(x, y)) : Optional.empty();
    }

    /** Inclusive normal-scene extended-zone minimum (5 for RuneLite). */
    public int normalMinZone() {
        return sceneZoneOffset();
    }

    /** Exclusive normal-scene extended-zone maximum (18 for RuneLite). */
    public int normalMaxZoneExclusive() {
        return sceneZoneOffset() + sceneZoneCount();
    }

    public record Point(int x, int y) {
    }
}
