package com.rspsi.editor.terrain;

import com.rspsi.editor.model.RegionNeighborhood;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.Tile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Shared terrain vertex lattice.
 *
 * <p>The document mode synchronizes all tile corners inside one document.
 * Neighborhood mode addresses vertices in world coordinates and synchronizes
 * every loaded tile sharing a border vertex, including adjacent 64x64
 * regions.</p>
 */
public final class TerrainVertexLattice {
    private final WorldDocument world;
    private final RegionNeighborhood neighborhood;

    public TerrainVertexLattice(WorldDocument world) {
        this.world = Objects.requireNonNull(world, "world");
        this.neighborhood = null;
    }

    public TerrainVertexLattice(RegionNeighborhood neighborhood) {
        this.world = null;
        this.neighborhood = Objects.requireNonNull(neighborhood, "neighborhood");
    }

    public int width() {
        ensureLocal();
        return world.width() + 1;
    }

    public int length() {
        ensureLocal();
        return world.length() + 1;
    }

    public int height(int plane, int vx, int vy) {
        ensureLocal();
        checkVertex(plane, vx, vy);
        if (vx < world.width() && vy < world.length()) {
            return world.tile(plane, vx, vy).snapshot().southWestHeight();
        }
        if (vx == world.width() && vy < world.length()) {
            return world.tile(plane, vx - 1, vy).snapshot().southEastHeight();
        }
        if (vy == world.length() && vx < world.width()) {
            return world.tile(plane, vx, vy - 1).snapshot().northWestHeight();
        }
        return world.tile(plane, vx - 1, vy - 1).snapshot().northEastHeight();
    }

    public Set<TileCoordinate> setHeight(int plane, int vx, int vy, int value) {
        ensureLocal();
        checkVertex(plane, vx, vy);
        Set<TileCoordinate> changed = new LinkedHashSet<>();
        updateLocal(changed, plane, vx, vy, Corner.SOUTH_WEST, value);
        updateLocal(changed, plane, vx - 1, vy, Corner.SOUTH_EAST, value);
        updateLocal(changed, plane, vx, vy - 1, Corner.NORTH_WEST, value);
        updateLocal(changed, plane, vx - 1, vy - 1, Corner.NORTH_EAST, value);
        return Set.copyOf(changed);
    }

    /** Returns a shared world-grid vertex from any loaded owning tile. */
    public int heightWorld(int plane, int worldVertexX, int worldVertexY) {
        ensureNeighborhood();
        if (plane < 0 || worldVertexX < 0 || worldVertexY < 0) {
            throw new IndexOutOfBoundsException("Invalid world vertex");
        }
        Optional<Integer> value = cornerValue(plane, worldVertexX, worldVertexY,
                Corner.SOUTH_WEST)
                .or(() -> cornerValue(plane, worldVertexX - 1, worldVertexY, Corner.SOUTH_EAST))
                .or(() -> cornerValue(plane, worldVertexX, worldVertexY - 1, Corner.NORTH_WEST))
                .or(() -> cornerValue(plane, worldVertexX - 1, worldVertexY - 1, Corner.NORTH_EAST));
        return value.orElseThrow(() -> new IndexOutOfBoundsException(
                "World vertex has no loaded owner: " + plane + "," + worldVertexX + "," + worldVertexY));
    }

    /**
     * Authors one world-grid vertex into every loaded adjacent region/tile
     * that owns the corner.
     */
    public Set<WorldTileAddress> setHeightWorld(int plane, int worldVertexX,
                                                int worldVertexY, int value) {
        ensureNeighborhood();
        if (plane < 0 || worldVertexX < 0 || worldVertexY < 0) {
            throw new IndexOutOfBoundsException("Invalid world vertex");
        }
        Set<WorldTileAddress> changed = new LinkedHashSet<>();
        updateWorld(changed, plane, worldVertexX, worldVertexY, Corner.SOUTH_WEST, value);
        updateWorld(changed, plane, worldVertexX - 1, worldVertexY, Corner.SOUTH_EAST, value);
        updateWorld(changed, plane, worldVertexX, worldVertexY - 1, Corner.NORTH_WEST, value);
        updateWorld(changed, plane, worldVertexX - 1, worldVertexY - 1, Corner.NORTH_EAST, value);
        return Set.copyOf(changed);
    }

    private void updateLocal(Set<TileCoordinate> changed, int plane, int x, int y,
                             Corner corner, int value) {
        if (!world.contains(plane, x, y)) return;
        Tile tile = world.tile(plane, x, y);
        writeCorner(tile, corner, value);
        changed.add(new TileCoordinate(plane, x, y));
    }

    private void updateWorld(Set<WorldTileAddress> changed, int plane, int worldX, int worldY,
                             Corner corner, int value) {
        if (worldX < 0 || worldY < 0) return;
        Tile tile = neighborhood.mutableTileAt(plane, worldX, worldY).orElse(null);
        if (tile == null) return;
        writeCorner(tile, corner, value);
        changed.add(WorldTileAddress.of(worldX, worldY, plane));
    }

    private Optional<Integer> cornerValue(int plane, int worldX, int worldY, Corner corner) {
        if (worldX < 0 || worldY < 0) return Optional.empty();
        return neighborhood.tileAt(plane, worldX, worldY).map(tile -> switch (corner) {
            case SOUTH_WEST -> tile.southWestHeight();
            case SOUTH_EAST -> tile.southEastHeight();
            case NORTH_EAST -> tile.northEastHeight();
            case NORTH_WEST -> tile.northWestHeight();
        });
    }

    private static void writeCorner(Tile tile, Corner corner, int value) {
        TileSnapshot before = tile.snapshot();
        int sw = before.southWestHeight();
        int se = before.southEastHeight();
        int ne = before.northEastHeight();
        int nw = before.northWestHeight();
        switch (corner) {
            case SOUTH_WEST -> sw = value;
            case SOUTH_EAST -> se = value;
            case NORTH_EAST -> ne = value;
            case NORTH_WEST -> nw = value;
        }
        TileSnapshot after = new TileSnapshot(sw, se, ne, nw,
                before.underlayId(), before.overlayId(), before.overlayShape(), before.overlayRotation(),
                before.flags(), before.objects(), TerrainHeightSource.authoredSource());
        tile.restore(after, TerrainHeightSource.authoredSource());
    }

    private void checkVertex(int plane, int vx, int vy) {
        if (plane < 0 || plane >= world.planes()
                || vx < 0 || vx > world.width()
                || vy < 0 || vy > world.length()) {
            throw new IndexOutOfBoundsException(
                    "Vertex outside lattice: " + plane + "," + vx + "," + vy);
        }
    }

    private void ensureLocal() {
        if (world == null) throw new IllegalStateException("Lattice is in world-neighborhood mode");
    }

    private void ensureNeighborhood() {
        if (neighborhood == null) {
            throw new IllegalStateException("Lattice is in document-local mode");
        }
    }

    private enum Corner { SOUTH_WEST, SOUTH_EAST, NORTH_EAST, NORTH_WEST }
}
