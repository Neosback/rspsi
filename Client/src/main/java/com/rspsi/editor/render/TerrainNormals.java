package com.rspsi.editor.render;

import com.rspsi.editor.model.RegionNeighborhood;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.terrain.TerrainVertexLattice;

import java.util.Objects;

/** Builds seam-safe terrain normals from the canonical shared height lattice. */
public final class TerrainNormals {
    private TerrainNormals() {
    }

    public static TerrainNormalTile buildTile(WorldDocument document,
                                              LightingProfile profile,
                                              int plane, int x, int y) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(profile, "profile");
        if (!document.contains(plane, x, y)) {
            throw new IndexOutOfBoundsException(
                    "Terrain tile outside document: " + plane + "," + x + "," + y);
        }
        return new TerrainNormalTile(
                cornerNormal(document, profile, plane, x, y),
                cornerNormal(document, profile, plane, x + 1, y),
                cornerNormal(document, profile, plane, x + 1, y + 1),
                cornerNormal(document, profile, plane, x, y + 1));
    }

    public static TerrainNormalTile buildTile(RegionNeighborhood neighborhood,
                                              LightingProfile profile,
                                              int plane, int worldX, int worldY) {
        Objects.requireNonNull(neighborhood, "neighborhood");
        Objects.requireNonNull(profile, "profile");
        if (neighborhood.tileAt(plane, worldX, worldY).isEmpty()) {
            throw new IndexOutOfBoundsException(
                    "Terrain tile is not loaded: " + plane + "," + worldX + "," + worldY);
        }
        TerrainVertexLattice lattice = new TerrainVertexLattice(neighborhood);
        return new TerrainNormalTile(
                worldCornerNormal(lattice, profile, plane, worldX, worldY),
                worldCornerNormal(lattice, profile, plane, worldX + 1, worldY),
                worldCornerNormal(lattice, profile, plane, worldX + 1, worldY + 1),
                worldCornerNormal(lattice, profile, plane, worldX, worldY + 1));
    }

    private static TerrainNormal worldCornerNormal(TerrainVertexLattice lattice,
                                                   LightingProfile profile,
                                                   int plane, int vx, int vy) {
        try {
            int heightDeltaX = lattice.heightWorld(plane, vx + 1, vy)
                    - lattice.heightWorld(plane, vx - 1, vy);
            int heightDeltaY = lattice.heightWorld(plane, vx, vy + 1)
                    - lattice.heightWorld(plane, vx, vy - 1);
            return com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateCornerNormal(
                    heightDeltaX, heightDeltaY, profile.heightScale());
        } catch (IndexOutOfBoundsException missingNeighbor) {
            return flat(profile);
        }
    }

    private static TerrainNormal cornerNormal(WorldDocument document,
                                              LightingProfile profile,
                                              int plane, int vx, int vy) {
        if (vx <= 0 || vy <= 0 || vx >= document.width() || vy >= document.length()) {
            return flat(profile);
        }
        int heightDeltaX = cornerHeight(document, plane, vx + 1, vy)
                - cornerHeight(document, plane, vx - 1, vy);
        int heightDeltaY = cornerHeight(document, plane, vx, vy + 1)
                - cornerHeight(document, plane, vx, vy - 1);
        return com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateCornerNormal(
                heightDeltaX, heightDeltaY, profile.heightScale());
    }

    private static TerrainNormal flat(LightingProfile profile) {
        return com.rspsi.osrs.rules.terrain.TerrainLightRules.calculateCornerNormal(
                0, 0, profile.heightScale());
    }

    private static int cornerHeight(WorldDocument document, int plane, int vx, int vy) {
        int x = Math.max(0, Math.min(document.width(), vx));
        int y = Math.max(0, Math.min(document.length(), vy));
        int tileX = Math.min(x, document.width() - 1);
        int tileY = Math.min(y, document.length() - 1);
        TileSnapshot tile = document.tile(plane, tileX, tileY).snapshot();
        if (x == document.width() && y == document.length()) return tile.northEastHeight();
        if (x == document.width()) return tile.southEastHeight();
        if (y == document.length()) return tile.northWestHeight();
        return tile.southWestHeight();
    }
}
