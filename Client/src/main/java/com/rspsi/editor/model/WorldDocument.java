package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    /** Returns an independent document copy without changing authored state. */
    public WorldDocument copy() {
        WorldDocument copy = new WorldDocument(width, length, planes);
        for (int plane = 0; plane < planes; plane++) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < length; y++) {
                    Tile original = tile(plane, x, y);
                    Tile destination = copy.tile(plane, x, y);
                    destination.restore(original.snapshot());
                    destination.heightSource(original.heightSource());
                }
            }
        }
        return copy;
    }

    public boolean contains(int plane, int x, int y) {
        return plane >= 0 && plane < planes && x >= 0 && x < width && y >= 0 && y < length;
    }

    public boolean contains(LocalTile coordinate) {
        return coordinate != null && contains(coordinate.plane(), coordinate.x(), coordinate.y());
    }

    public Optional<Tile> tileOpt(LocalTile coordinate) {
        if (!contains(coordinate)) return Optional.empty();
        return Optional.of(tiles[coordinate.plane()][coordinate.x()][coordinate.y()]);
    }

    /** @deprecated Use LocalTile so coordinate space is compiler-visible. */
    @Deprecated
    public boolean contains(TileCoordinate coordinate) {
        return coordinate != null && contains(LocalTile.from(coordinate));
    }

    /** @deprecated Use LocalTile so coordinate space is compiler-visible. */
    @Deprecated
    public Optional<Tile> tileOpt(TileCoordinate coordinate) {
        return coordinate == null ? Optional.empty() : tileOpt(LocalTile.from(coordinate));
    }

    public Tile tile(int plane, int x, int y) {
        if (!contains(plane, x, y)) {
            throw new IndexOutOfBoundsException("Tile outside world: " + plane + "," + x + "," + y);
        }
        return tiles[plane][x][y];
    }

    public Tile tile(LocalTile coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return tile(coordinate.plane(), coordinate.x(), coordinate.y());
    }

    /** @deprecated Use LocalTile so absolute world coordinates cannot cross this boundary. */
    @Deprecated
    public Tile tile(TileCoordinate coordinate) {
        return tile(LocalTile.from(coordinate));
    }

    /**
     * Returns the effective OSRS plane for authored terrain/object data at a
     * local tile. A bridge flag on authored plane 1 demotes the complete
     * column by one plane; plane 0 consequently has no effective collision
     * surface at that column, matching the scene-builder ordering.
     */
    public int effectivePlane(int authoredPlane, int x, int y) {
        if (authoredPlane < 0 || authoredPlane >= planes) {
            throw new IndexOutOfBoundsException("Invalid authored plane: " + authoredPlane);
        }
        tile(authoredPlane, x, y);
        boolean bridged = planes > 1 && OsrsTileFlags.hasBridge(tile(1, x, y).snapshot().flags());
        return bridged ? authoredPlane - 1 : authoredPlane;
    }

    public int effectivePlane(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        return effectivePlane(coordinate.plane(), coordinate.x(), coordinate.y());
    }

    /** Returns the bridge relation for one authored tile, when its flag is set. */
    public Optional<BridgeLink> bridgeLink(TileCoordinate coordinate) {
        if (coordinate == null || coordinate.plane() == 0
                || coordinate.plane() >= planes
                || coordinate.x() < 0 || coordinate.x() >= width
                || coordinate.y() < 0 || coordinate.y() >= length) {
            return Optional.empty();
        }
        if (!OsrsTileFlags.hasBridge(tile(1, coordinate.x(), coordinate.y()).snapshot().flags())) {
            return Optional.empty();
        }
        return Optional.of(new BridgeLink(coordinate,
                new TileCoordinate(effectivePlane(coordinate), coordinate.x(), coordinate.y())));
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
