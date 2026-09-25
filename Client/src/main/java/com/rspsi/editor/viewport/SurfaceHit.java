package com.rspsi.editor.viewport;

import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;

import java.util.Objects;
import java.util.Optional;

/**
 * Renderer-independent answer to "what semantic map surface is under this pointer?".
 *
 * <p>The exact ray intersection remains renderer-owned. Tools receive only
 * stable editor semantics: the world tile that was hit, optional object anchor
 * and object id, and the resolved authored object placement when the host can
 * provide one.</p>
 */
public record SurfaceHit(
        WorldTile tile,
        int objectId,
        Optional<WorldTile> objectAnchor,
        Optional<WorldObject> object) {

    public SurfaceHit {
        tile = Objects.requireNonNull(tile, "tile");
        if (objectId < -1) {
            throw new IllegalArgumentException("Object id must be -1 for terrain or non-negative");
        }
        objectAnchor = objectAnchor == null ? Optional.empty() : objectAnchor;
        object = object == null ? Optional.empty() : object;

        if (objectId < 0 && (objectAnchor.isPresent() || object.isPresent())) {
            throw new IllegalArgumentException("Terrain hits cannot carry object data");
        }
        objectAnchor.ifPresent(anchor -> {
            if (anchor.plane() != tile.plane()) {
                throw new IllegalArgumentException("Object anchor must be on the hit plane");
            }
        });
        object.ifPresent(placement -> {
            if (placement.id() != objectId) {
                throw new IllegalArgumentException("Resolved object id must match hit object id");
            }
        });
    }

    public static SurfaceHit terrain(WorldTile tile) {
        return new SurfaceHit(tile, -1, Optional.empty(), Optional.empty());
    }

    public static SurfaceHit object(
            WorldTile tile,
            int objectId,
            WorldTile objectAnchor,
            WorldObject placement) {
        return new SurfaceHit(
                tile,
                objectId,
                Optional.ofNullable(objectAnchor),
                Optional.ofNullable(placement));
    }

    public boolean objectHit() {
        return objectId >= 0;
    }

    /** The object's anchor when available, otherwise the exact ray-hit tile. */
    public WorldTile targetTile() {
        return objectAnchor.orElse(tile);
    }
}
