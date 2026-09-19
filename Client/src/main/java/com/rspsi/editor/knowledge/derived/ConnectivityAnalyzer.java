package com.rspsi.editor.knowledge.derived;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Layer 3 Derived Analyzer: Connected components analysis for paths, wall runs, walkability, and water bodies.
 */
public final class ConnectivityAnalyzer {
    private static final int[][] CARDINAL_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ConnectivityAnalyzer() {}

    /**
     * Finds connected components (contiguous tile clusters) satisfying the given predicate on the same plane.
     */
    public static List<Set<TileCoordinate>> findComponents(WorldDocument document, int plane,
                                                           Predicate<TileSnapshot> tilePredicate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(tilePredicate, "tilePredicate");
        if (plane < 0 || plane >= document.planes()) return List.of();

        List<Set<TileCoordinate>> components = new ArrayList<>();
        boolean[][] visited = new boolean[document.width()][document.length()];

        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                if (visited[x][y]) continue;
                TileSnapshot snap = document.tile(plane, x, y).snapshot();
                if (!tilePredicate.test(snap)) continue;

                // BFS flood-fill component
                Set<TileCoordinate> component = new HashSet<>();
                Queue<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{x, y});
                visited[x][y] = true;

                while (!queue.isEmpty()) {
                    int[] cur = queue.poll();
                    int cx = cur[0];
                    int cy = cur[1];
                    component.add(new TileCoordinate(plane, cx, cy));

                    for (int[] off : CARDINAL_OFFSETS) {
                        int nx = cx + off[0];
                        int ny = cy + off[1];
                        if (nx >= 0 && nx < document.width() && ny >= 0 && ny < document.length()
                                && !visited[nx][ny]) {
                            TileSnapshot neighbor = document.tile(plane, nx, ny).snapshot();
                            if (tilePredicate.test(neighbor)) {
                                visited[nx][ny] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
                components.add(Collections.unmodifiableSet(component));
            }
        }
        return Collections.unmodifiableList(components);
    }

    /** Finds contiguous paths/roads for a specific overlay ID. */
    public static List<Set<TileCoordinate>> findOverlayComponents(WorldDocument document, int plane, int overlayId) {
        return findComponents(document, plane, tile -> tile.overlayId() == overlayId);
    }

    /** Finds contiguous wall runs (tiles containing wall objects). */
    public static List<Set<TileCoordinate>> findWallRuns(WorldDocument document, int plane) {
        return findComponents(document, plane, tile -> {
            for (WorldObject obj : tile.objects()) {
                if (obj.category() == ObjectCategory.WALL) return true;
            }
            return false;
        });
    }

    /** Finds contiguous walkable areas (unblocked tiles). */
    public static List<Set<TileCoordinate>> findWalkableComponents(WorldDocument document, int plane) {
        return findComponents(document, plane, tile -> !OsrsTileFlags.isBlocked(tile.flags()));
    }
}
