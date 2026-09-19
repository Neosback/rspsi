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

/**
 * Layer 3 Derived Analyzer: Extracts architectural structures (rooms, doorways, building footprints).
 */
public final class StructureAnalyzer {
    private static final int[][] CARDINAL_OFFSETS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private StructureAnalyzer() {}

    public record Room(
            int plane,
            Set<TileCoordinate> interiorTiles,
            Set<TileCoordinate> perimeterWalls
    ) {
        public Room {
            interiorTiles = Set.copyOf(interiorTiles);
            perimeterWalls = Set.copyOf(perimeterWalls);
        }
    }

    public record BuildingFootprint(
            int plane,
            int minX,
            int maxX,
            int minY,
            int maxY,
            Set<TileCoordinate> tiles
    ) {
        public BuildingFootprint {
            tiles = Set.copyOf(tiles);
        }

        public int width() {
            return maxX - minX + 1;
        }

        public int length() {
            return maxY - minY + 1;
        }
    }

    /**
     * Identifies building footprints from roof flags or roof objects.
     */
    public static List<BuildingFootprint> findBuildingFootprints(WorldDocument document, int plane) {
        Objects.requireNonNull(document, "document");
        if (plane < 0 || plane >= document.planes()) return List.of();

        List<Set<TileCoordinate>> clusters = ConnectivityAnalyzer.findComponents(document, plane, tile -> {
            if (OsrsTileFlags.removesRoofs(tile.flags())) return true;
            for (WorldObject obj : tile.objects()) {
                if (obj.shape().map(s -> s.id() >= 12 && s.id() <= 21).orElse(false)) {
                    return true;
                }
            }
            return false;
        });

        List<BuildingFootprint> footprints = new ArrayList<>();
        for (Set<TileCoordinate> cluster : clusters) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (TileCoordinate coord : cluster) {
                if (coord.x() < minX) minX = coord.x();
                if (coord.x() > maxX) maxX = coord.x();
                if (coord.y() < minY) minY = coord.y();
                if (coord.y() > maxY) maxY = coord.y();
            }
            footprints.add(new BuildingFootprint(plane, minX, maxX, minY, maxY, cluster));
        }
        return Collections.unmodifiableList(footprints);
    }

    /**
     * Identifies enclosed rooms bounded by walls on a specific plane.
     */
    public static List<Room> findRooms(WorldDocument document, int plane) {
        Objects.requireNonNull(document, "document");
        if (plane < 0 || plane >= document.planes()) return List.of();

        // 1. Build wall occupancy grid
        boolean[][] isWall = new boolean[document.width()][document.length()];
        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                TileSnapshot snap = document.tile(plane, x, y).snapshot();
                for (WorldObject obj : snap.objects()) {
                    if (obj.category() == ObjectCategory.WALL) {
                        isWall[x][y] = true;
                        break;
                    }
                }
            }
        }

        // 2. Flood-fill from document edges to mark exterior space
        boolean[][] visited = new boolean[document.width()][document.length()];
        Queue<int[]> queue = new ArrayDeque<>();

        for (int x = 0; x < document.width(); x++) {
            enqueueIfNonWall(x, 0, isWall, visited, queue);
            enqueueIfNonWall(x, document.length() - 1, isWall, visited, queue);
        }
        for (int y = 0; y < document.length(); y++) {
            enqueueIfNonWall(0, y, isWall, visited, queue);
            enqueueIfNonWall(document.width() - 1, y, isWall, visited, queue);
        }

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cy = cur[1];
            for (int[] off : CARDINAL_OFFSETS) {
                int nx = cx + off[0];
                int ny = cy + off[1];
                if (nx >= 0 && nx < document.width() && ny >= 0 && ny < document.length()
                        && !visited[nx][ny] && !isWall[nx][ny]) {
                    visited[nx][ny] = true;
                    queue.add(new int[]{nx, ny});
                }
            }
        }

        // 3. Any unvisited non-wall tiles belong to enclosed rooms
        List<Room> rooms = new ArrayList<>();
        boolean[][] roomVisited = new boolean[document.width()][document.length()];

        for (int x = 0; x < document.width(); x++) {
            for (int y = 0; y < document.length(); y++) {
                if (visited[x][y] || isWall[x][y] || roomVisited[x][y]) continue;

                Set<TileCoordinate> interior = new HashSet<>();
                Set<TileCoordinate> perimeter = new HashSet<>();
                Queue<int[]> rQueue = new ArrayDeque<>();
                rQueue.add(new int[]{x, y});
                roomVisited[x][y] = true;

                while (!rQueue.isEmpty()) {
                    int[] cur = rQueue.poll();
                    int cx = cur[0];
                    int cy = cur[1];
                    interior.add(new TileCoordinate(plane, cx, cy));

                    for (int[] off : CARDINAL_OFFSETS) {
                        int nx = cx + off[0];
                        int ny = cy + off[1];
                        if (nx >= 0 && nx < document.width() && ny >= 0 && ny < document.length()) {
                            if (isWall[nx][ny]) {
                                perimeter.add(new TileCoordinate(plane, nx, ny));
                            } else if (!roomVisited[nx][ny] && !visited[nx][ny]) {
                                roomVisited[nx][ny] = true;
                                rQueue.add(new int[]{nx, ny});
                            }
                        }
                    }
                }

                // Minimum room size threshold
                if (interior.size() >= 2) {
                    rooms.add(new Room(plane, interior, perimeter));
                }
            }
        }

        return Collections.unmodifiableList(rooms);
    }

    private static void enqueueIfNonWall(int x, int y, boolean[][] isWall, boolean[][] visited, Queue<int[]> queue) {
        if (!isWall[x][y] && !visited[x][y]) {
            visited[x][y] = true;
            queue.add(new int[]{x, y});
        }
    }
}
