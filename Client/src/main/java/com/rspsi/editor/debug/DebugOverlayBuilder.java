package com.rspsi.editor.debug;

import com.rspsi.editor.collision.CollisionMap;
import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileInspectorSnapshot;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldWindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds semantic overlay data for the current document and world window. */
public final class DebugOverlayBuilder {
    private static final int REGION_SIZE = 64;
    private static final int CHUNK_SIZE = 8;

    public DebugOverlaySnapshot build(WorldDocument document, WorldWindow worldWindow,
                                      CollisionMap collision, DebugOverlaySettings settings) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(worldWindow, "worldWindow");
        Objects.requireNonNull(settings, "settings");
        if (worldWindow.width() != document.width() || worldWindow.length() != document.length()) {
            throw new IllegalArgumentException("World window must match document dimensions");
        }
        Map<Integer, List<DebugTileSnapshot>> tiles = new LinkedHashMap<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            List<DebugTileSnapshot> planeTiles = new ArrayList<>(document.width() * document.length());
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    TileSnapshot tile = document.tile(coordinate).snapshot();
                    WorldTileAddress address = WorldTileAddress.of(
                            worldWindow.worldX(coordinate), worldWindow.worldY(coordinate), plane);
                    boolean bridge = document.bridgeLink(coordinate).isPresent();
                    boolean roofRelated = (tile.flags() & OsrsTileFlags.REMOVE_ROOFS) != 0;
                    var collisionSnapshot = settings.enabled(DebugOverlayMode.COLLISION)
                            && contains(collision, coordinate)
                            ? java.util.Optional.of(CollisionTileSnapshot.from(collision, coordinate))
                            : java.util.Optional.<CollisionTileSnapshot>empty();
                    int effectivePlane = bridge
                            ? Math.max(0, OsrsCollisionBuilder.resolvedPlane(document, plane, x, y))
                            : plane;
                    planeTiles.add(new DebugTileSnapshot(coordinate, address,
                            new TileInspectorSnapshot(address, tile, bridge, roofRelated),
                            collisionSnapshot, effectivePlane));
                }
            }
            tiles.put(plane, List.copyOf(planeTiles));
        }
        return new DebugOverlaySnapshot(settings, worldWindow,
                buildGridLines(worldWindow, settings), tiles);
    }

    private static boolean contains(CollisionMap collision, TileCoordinate coordinate) {
        return collision != null && collision.contains(coordinate);
    }

    private static List<DebugGridLine> buildGridLines(WorldWindow window, DebugOverlaySettings settings) {
        List<DebugGridLine> lines = new ArrayList<>();
        int minX = window.originX();
        int minY = window.originY();
        int maxX = minX + window.width();
        int maxY = minY + window.length();
        if (settings.enabled(DebugOverlayMode.TILE_GRID)) {
            addVerticalLines(lines, DebugGridLevel.TILE, minX, maxX, minY, maxY, 1);
            addHorizontalLines(lines, DebugGridLevel.TILE, minY, maxY, minX, maxX, 1);
        }
        if (settings.enabled(DebugOverlayMode.CHUNK_GRID)) {
            addVerticalLines(lines, DebugGridLevel.CHUNK, minX, maxX, minY, maxY, CHUNK_SIZE);
            addHorizontalLines(lines, DebugGridLevel.CHUNK, minY, maxY, minX, maxX, CHUNK_SIZE);
        }
        if (settings.enabled(DebugOverlayMode.REGION_GRID)) {
            addVerticalLines(lines, DebugGridLevel.REGION, minX, maxX, minY, maxY, REGION_SIZE);
            addHorizontalLines(lines, DebugGridLevel.REGION, minY, maxY, minX, maxX, REGION_SIZE);
        }
        if (settings.enabled(DebugOverlayMode.LOADED_WORLD_WINDOW)) {
            lines.add(new DebugGridLine(DebugGridLevel.WORLD_WINDOW, minX, minY, maxX, minY));
            lines.add(new DebugGridLine(DebugGridLevel.WORLD_WINDOW, maxX, minY, maxX, maxY));
            lines.add(new DebugGridLine(DebugGridLevel.WORLD_WINDOW, maxX, maxY, minX, maxY));
            lines.add(new DebugGridLine(DebugGridLevel.WORLD_WINDOW, minX, maxY, minX, minY));
        }
        return List.copyOf(lines);
    }

    private static void addVerticalLines(List<DebugGridLine> lines, DebugGridLevel level,
                                         int min, int max, int startY, int endY, int spacing) {
        int first = min + Math.floorMod(-min, spacing);
        for (int x = first; x <= max; x += spacing) {
            lines.add(new DebugGridLine(level, x, startY, x, endY));
        }
    }

    private static void addHorizontalLines(List<DebugGridLine> lines, DebugGridLevel level,
                                           int min, int max, int startX, int endX, int spacing) {
        int first = min + Math.floorMod(-min, spacing);
        for (int y = first; y <= max; y += spacing) {
            lines.add(new DebugGridLine(level, startX, y, endX, y));
        }
    }
}
