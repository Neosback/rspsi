package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Canonical mutable world/document state for editor operations.
 *
 * This type deliberately contains data only. Cache formats, renderers, and UI
 * frontends adapt to it rather than becoming part of the document model.
 */
public class WorldDocument {
    public static final int DEFAULT_PLANES = 4;

    private final int width;
    private final int length;
    private final int planes;
    private final Tile[][][] tiles;

    public WorldDocument(int width, int length) {
        this(width, length, DEFAULT_PLANES);
    }

    public WorldDocument(int width, int length, int planes) {
        if (width <= 0 || length <= 0 || planes <= 0) {
            throw new IllegalArgumentException("World dimensions must be positive");
        }
        this.width = width;
        this.length = length;
        this.planes = planes;
        this.tiles = new Tile[planes][width][length];
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    tiles[plane][x][y] = new Tile(new TileCoordinate(plane, x, y));
                }
            }
        }
    }

    public int width() {
        return width;
    }

    public int length() {
        return length;
    }

    public int planes() {
        return planes;
    }

    public Tile tile(int plane, int x, int y) {
        if (plane < 0 || plane >= planes || x < 0 || x >= width || y < 0 || y >= length) {
            throw new IndexOutOfBoundsException("Tile outside world: " + plane + "," + x + "," + y);
        }
        return tiles[plane][x][y];
    }

    public Tile tile(TileCoordinate coordinate) {
        return tile(coordinate.plane(), coordinate.x(), coordinate.y());
    }

    /** Returns the bridge relation for one authored tile, when its flag is set. */
    public Optional<BridgeLink> bridgeLink(TileCoordinate coordinate) {
        if (coordinate == null || coordinate.plane() == 0
                || coordinate.plane() >= planes
                || coordinate.x() >= width || coordinate.y() >= length) {
            return Optional.empty();
        }
        if (!OsrsTileFlags.hasBridge(tile(1, coordinate.x(), coordinate.y()).snapshot().flags())) {
            return Optional.empty();
        }
        return Optional.of(new BridgeLink(coordinate,
                new TileCoordinate(coordinate.plane() - 1, coordinate.x(), coordinate.y())));
    }

    /** Returns all authored-plane bridge links represented by the document flags. */
    public List<BridgeLink> bridgeLinks() {
        if (planes <= 1) return List.of();
        List<BridgeLink> links = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < length; y++) {
                if (!OsrsTileFlags.hasBridge(tile(1, x, y).snapshot().flags())) continue;
                for (int plane = 1; plane < planes; plane++) {
                    links.add(new BridgeLink(new TileCoordinate(plane, x, y),
                            new TileCoordinate(plane - 1, x, y)));
                }
            }
        }
        return List.copyOf(links);
    }
}
