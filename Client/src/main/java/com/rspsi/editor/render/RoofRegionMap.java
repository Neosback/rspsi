package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * RuneLite-compatible connected roof-region ids derived from tile flag 0x4.
 *
 * <p>{@code RSSceneMixin.buildRoofs()} flood-fills blocking tiles using all
 * eight neighbours. Each blocking component also labels its immediate
 * non-blocking perimeter, but traversal does not continue through that
 * perimeter. The resulting region ids are what RuneLite's POSITION, HOVERED,
 * DESTINATION and BETWEEN modes select at draw time.</p>
 */
public final class RoofRegionMap {
    private final int baseX;
    private final int baseY;
    private final int planes;
    private final int width;
    private final int length;
    private final boolean[] present;
    private final int[] flags;
    private final int[] regionIds;
    private final int regionCount;

    private RoofRegionMap(int baseX, int baseY, int planes, int width, int length,
                          boolean[] present, int[] flags, int[] regionIds, int regionCount) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.planes = planes;
        this.width = width;
        this.length = length;
        this.present = present;
        this.flags = flags;
        this.regionIds = regionIds;
        this.regionCount = regionCount;
    }

    public static RoofRegionMap build(SceneWindow window, List<SceneTileSnapshot> tiles) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(tiles, "tiles");

        int width = window.sourceRegions().worldWindow().width();
        int length = window.sourceRegions().worldWindow().length();
        int planes = window.planes();
        int size = Math.multiplyExact(Math.multiplyExact(planes, width), length);
        boolean[] present = new boolean[size];
        int[] flags = new int[size];

        for (SceneTileSnapshot tile : tiles) {
            int plane = tile.authoredPlane();
            int x = tile.worldAddress().worldX() - window.sceneBaseX();
            int y = tile.worldAddress().worldY() - window.sceneBaseY();
            if (plane < 0 || plane >= planes || x < 0 || x >= width || y < 0 || y >= length) {
                continue;
            }
            int index = index(plane, x, y, width, length);
            present[index] = true;
            flags[index] = tile.tileFlags();
        }

        int[] ids = new int[size];
        int nextRegion = 1;
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        // RuneLite scans y first, then x, and assigns monotonically increasing ids.
        for (int plane = 0; plane < planes; plane++) {
            for (int y = 0; y < length; y++) {
                for (int x = 0; x < width; x++) {
                    int start = index(plane, x, y, width, length);
                    if (!present[start] || ids[start] != 0
                            || !OsrsTileFlags.removesRoofs(flags[start])) {
                        continue;
                    }

                    queue.addLast(start);
                    while (!queue.isEmpty()) {
                        int current = queue.removeFirst();
                        if (ids[current] != 0) {
                            continue;
                        }

                        int local = current - plane * width * length;
                        int currentX = local / length;
                        int currentY = local % length;
                        if (OsrsTileFlags.removesRoofs(flags[current])) {
                            for (int dx = -1; dx <= 1; dx++) {
                                for (int dy = -1; dy <= 1; dy++) {
                                    if (dx == 0 && dy == 0) {
                                        continue;
                                    }
                                    int nx = currentX + dx;
                                    int ny = currentY + dy;
                                    if (nx < 0 || nx >= width || ny < 0 || ny >= length) {
                                        continue;
                                    }
                                    int neighbour = index(plane, nx, ny, width, length);
                                    if (present[neighbour]) {
                                        queue.addLast(neighbour);
                                    }
                                }
                            }
                        }

                        // Match RSSceneMixin: non-blocking neighbours are labelled
                        // too, but because they never enqueue neighbours they form
                        // only the one-tile perimeter around the blocking component.
                        ids[current] = nextRegion;
                    }
                    nextRegion++;
                }
            }
        }

        return new RoofRegionMap(window.sceneBaseX(), window.sceneBaseY(), planes,
                width, length, present, flags, ids, nextRegion - 1);
    }

    public int baseX() {
        return baseX;
    }

    public int baseY() {
        return baseY;
    }

    public int planes() {
        return planes;
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public int regionCount() {
        return regionCount;
    }

    public boolean contains(int plane, int sceneX, int sceneY) {
        return plane >= 0 && plane < planes
                && sceneX >= 0 && sceneX < width
                && sceneY >= 0 && sceneY < length
                && present[index(plane, sceneX, sceneY, width, length)];
    }

    public boolean blocking(int plane, int sceneX, int sceneY) {
        if (!contains(plane, sceneX, sceneY)) {
            return false;
        }
        return OsrsTileFlags.removesRoofs(
                flags[index(plane, sceneX, sceneY, width, length)]);
    }

    public int regionId(int plane, int sceneX, int sceneY) {
        if (!contains(plane, sceneX, sceneY)) {
            return 0;
        }
        return regionIds[index(plane, sceneX, sceneY, width, length)];
    }

    public int regionIdWorld(int plane, int worldX, int worldY) {
        return regionId(plane, worldX - baseX, worldY - baseY);
    }

    private static int index(int plane, int x, int y, int width, int length) {
        return (plane * width + x) * length + y;
    }
}
