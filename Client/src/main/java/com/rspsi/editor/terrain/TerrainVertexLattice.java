package com.rspsi.editor.terrain;

import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared vertex lattice over a WorldDocument. Each plane contains
 * (width + 1) x (length + 1) canonical vertices.
 */
public final class TerrainVertexLattice {
    private final WorldDocument world;

    public TerrainVertexLattice(WorldDocument world) {
        this.world = java.util.Objects.requireNonNull(world, "world");
    }

    public int width() { return world.width() + 1; }
    public int length() { return world.length() + 1; }

    public int height(int plane, int vx, int vy) {
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
        checkVertex(plane, vx, vy);
        Set<TileCoordinate> changed = new LinkedHashSet<>();
        update(changed, plane, vx, vy, Corner.SOUTH_WEST, value);
        update(changed, plane, vx - 1, vy, Corner.SOUTH_EAST, value);
        update(changed, plane, vx, vy - 1, Corner.NORTH_WEST, value);
        update(changed, plane, vx - 1, vy - 1, Corner.NORTH_EAST, value);
        return Set.copyOf(changed);
    }

    private void update(Set<TileCoordinate> changed, int plane, int x, int y, Corner corner, int value) {
        if (!world.contains(plane, x, y)) return;
        var tile = world.tile(plane, x, y);
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
        changed.add(new TileCoordinate(plane, x, y));
    }

    private void checkVertex(int plane, int vx, int vy) {
        if (plane < 0 || plane >= world.planes()
                || vx < 0 || vx > world.width()
                || vy < 0 || vy > world.length()) {
            throw new IndexOutOfBoundsException("Vertex outside lattice: " + plane + "," + vx + "," + vy);
        }
    }

    private enum Corner { SOUTH_WEST, SOUTH_EAST, NORTH_EAST, NORTH_WEST }
}
