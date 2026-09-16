package com.rspsi.editor.collision;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;

import java.util.Objects;

/** Builds the terrain/bridge portion of canonical OSRS collision. */
public final class OsrsCollisionBuilder {
    public static final int BLOCK_MAP_SQUARE = 0x1;
    public static final int LINK_BELOW = 0x2;
    public static final int REMOVE_ROOFS = 0x4;

    private static final int BRIDGE_FLAG_PLANE = 1;

    private OsrsCollisionBuilder() {
    }

    /**
     * Applies map-square blocking and roof flags, resolving the OSRS bridge
     * relationship before writing collision. Object collision is intentionally
     * a separate definition-backed step and is not guessed here.
     */
    public static CollisionMap fromTerrain(WorldDocument document) {
        Objects.requireNonNull(document, "document");
        CollisionMap collision = new CollisionMap(document.width(), document.length(), document.planes());
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    TileSnapshot tile = document.tile(plane, x, y).snapshot();
                    int mask = terrainMask(tile.flags());
                    if (mask == 0) {
                        continue;
                    }
                    int resolvedPlane = resolvedPlane(document, plane, x, y);
                    if (resolvedPlane >= 0 && resolvedPlane < document.planes()) {
                        collision.add(new com.rspsi.editor.model.TileCoordinate(resolvedPlane, x, y), mask);
                    }
                }
            }
        }
        return collision;
    }

    /** Resolves the authored plane using the LINK_BELOW flag on plane 1. */
    public static int resolvedPlane(WorldDocument document, int authoredPlane, int x, int y) {
        Objects.requireNonNull(document, "document");
        if (authoredPlane < 0 || authoredPlane >= document.planes()) {
            throw new IndexOutOfBoundsException("Invalid authored plane: " + authoredPlane);
        }
        boolean bridged = document.planes() > BRIDGE_FLAG_PLANE
                && (document.tile(BRIDGE_FLAG_PLANE, x, y).snapshot().flags() & LINK_BELOW) != 0;
        return bridged ? authoredPlane - 1 : authoredPlane;
    }

    private static int terrainMask(int flags) {
        int mask = 0;
        if ((flags & BLOCK_MAP_SQUARE) != 0) {
            mask |= CollisionFlag.BLOCK_WALK;
        }
        if ((flags & REMOVE_ROOFS) != 0) {
            mask |= CollisionFlag.ROOF;
        }
        return mask;
    }
}
