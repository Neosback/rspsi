package com.rspsi.editor.render;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Frame-time RuneLite roof-removal inputs.
 *
 * <p>The flag values match {@code net.runelite.api.Constants}. Coordinates are
 * scene-local tile coordinates. This state is intentionally transient: it is
 * derived from the current player, hovered tile, destination and camera rather
 * than stored in authored map data.</p>
 */
public record RoofRemovalState(
        int modeFlags,
        Optional<ScenePoint> player,
        Optional<ScenePoint> hovered,
        Optional<ScenePoint> destination,
        Optional<ScenePoint> camera,
        int cameraPitch
) {
    public static final int POSITION = 1;
    public static final int HOVERED = 2;
    public static final int DESTINATION = 4;
    public static final int BETWEEN = 8;
    public static final int ALL_FLAGS = POSITION | HOVERED | DESTINATION | BETWEEN;
    public static final int BETWEEN_PITCH_LIMIT = 310;

    public RoofRemovalState {
        if ((modeFlags & ~ALL_FLAGS) != 0 || modeFlags < 0) {
            throw new IllegalArgumentException("Unknown roof-removal mode flags: " + modeFlags);
        }
        player = Objects.requireNonNull(player, "player");
        hovered = Objects.requireNonNull(hovered, "hovered");
        destination = Objects.requireNonNull(destination, "destination");
        camera = Objects.requireNonNull(camera, "camera");
        if (cameraPitch < 0) {
            throw new IllegalArgumentException("Camera pitch cannot be negative");
        }
    }

    public RoofRemovalState(int modeFlags,
                            ScenePoint player,
                            ScenePoint hovered,
                            ScenePoint destination,
                            ScenePoint camera,
                            int cameraPitch) {
        this(modeFlags,
                Optional.ofNullable(player),
                Optional.ofNullable(hovered),
                Optional.ofNullable(destination),
                Optional.ofNullable(camera),
                cameraPitch);
    }

    public static RoofRemovalState disabled() {
        return new RoofRemovalState(0,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0);
    }

    public boolean enabled() {
        return modeFlags != 0;
    }

    public boolean has(int flag) {
        return (modeFlags & flag) != 0;
    }

    /**
     * Resolves the exact set of connected roof ids selected by RuneLite for
     * one active scene plane.
     */
    public Set<Integer> selectedRegionIds(RoofRegionMap regions, int activePlane) {
        Objects.requireNonNull(regions, "regions");
        if (!enabled() || activePlane < 0 || activePlane >= regions.planes()) {
            return Set.of();
        }

        Set<Integer> selected = new LinkedHashSet<>();
        if (has(POSITION)) {
            player.ifPresent(point -> addRegion(selected, regions, activePlane, point));
        }
        if (has(HOVERED)) {
            hovered.ifPresent(point -> addRegion(selected, regions, activePlane, point));
        }
        if (has(DESTINATION)) {
            destination.ifPresent(point -> addRegion(selected, regions, activePlane, point));
        }
        if (has(BETWEEN) && cameraPitch < BETWEEN_PITCH_LIMIT
                && player.isPresent() && camera.isPresent()) {
            addBetweenRegions(selected, regions, activePlane, camera.get(), player.get());
        }
        return Set.copyOf(selected);
    }

    private static void addRegion(Set<Integer> selected, RoofRegionMap regions,
                                  int plane, ScenePoint point) {
        int id = regions.regionId(plane, point.x(), point.y());
        // RuneLite may temporarily add id 0, but its draw condition explicitly
        // requires var30 != 0. Omitting zero is therefore behaviorally exact.
        if (id != 0) {
            selected.add(id);
        }
    }

    private static void addBetweenRegions(Set<Integer> selected, RoofRegionMap regions,
                                          int plane, ScenePoint camera, ScenePoint player) {
        if (!regions.contains(plane, camera.x(), camera.y())
                || !regions.contains(plane, player.x(), player.y())) {
            return;
        }

        int x = camera.x();
        int y = camera.y();
        int dx = Math.abs(player.x() - x);
        int stepX = Integer.compare(player.x(), x);
        int dy = -Math.abs(player.y() - y);
        int stepY = Integer.compare(player.y(), y);
        int error = dx + dy;

        // Deliberately use RuneLite's if/else variant rather than the common
        // two-if Bresenham form, and exclude the player's final tile.
        while (x != player.x() || y != player.y()) {
            if (regions.blocking(plane, x, y)) {
                int id = regions.regionId(plane, x, y);
                if (id != 0) {
                    selected.add(id);
                }
            }

            int twiceError = 2 * error;
            if (twiceError >= dy) {
                error += dy;
                x += stepX;
            } else {
                error += dx;
                y += stepY;
            }
        }
    }

    public record ScenePoint(int x, int y) {
        public ScenePoint {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException("Scene tile coordinates cannot be negative");
            }
        }
    }
}
