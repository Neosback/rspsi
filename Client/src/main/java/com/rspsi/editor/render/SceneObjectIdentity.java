package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;

/**
 * Stable semantic identity for one placed scene object.
 *
 * <p>The identity is derived only from authored placement data and the placed
 * definition footprint, never from transient packet, draw-command, or GPU
 * indices. Rebuilding the same scene therefore reproduces the same identity,
 * while moving or rotating the object produces a different one. Multi-part
 * renderables such as type-2 walls and shape-8 wall decorations deliberately
 * share one identity.</p>
 */
public record SceneObjectIdentity(
        boolean present,
        int objectId,
        ObjectCategory category,
        int shape,
        int rotation,
        int authoredPlane,
        int anchorX,
        int anchorY,
        int footprintWidth,
        int footprintLength
) {
    private static final SceneObjectIdentity NONE =
            new SceneObjectIdentity(false, -1, ObjectCategory.GROUND,
                    0, 0, 0, 0, 0, 0, 0);

    public SceneObjectIdentity {
        category = Objects.requireNonNull(category, "category");
        if (present && (objectId < 0 || shape < 0 || rotation < 0 || rotation > 3
                || authoredPlane < 0 || anchorX < 0 || anchorY < 0
                || footprintWidth <= 0 || footprintLength <= 0)) {
            throw new IllegalArgumentException("Invalid scene object identity");
        }
    }

    public static SceneObjectIdentity none() {
        return NONE;
    }

    public static SceneObjectIdentity of(WorldObject object, int footprintWidth, int footprintLength) {
        Objects.requireNonNull(object, "object");
        return new SceneObjectIdentity(true, object.id(), object.category(), object.type(),
                object.rotation(), object.plane(), object.x(), object.y(),
                footprintWidth, footprintLength);
    }

    /**
     * Reprojects only the anchor coordinate. Authored identity fields remain
     * unchanged so local packets can be promoted to world-addressed scenes.
     */
    public SceneObjectIdentity withAnchor(TileCoordinate anchor) {
        Objects.requireNonNull(anchor, "anchor");
        if (!present) return this;
        if (anchor.plane() != authoredPlane) {
            throw new IllegalArgumentException("Scene object anchor plane must remain authored plane");
        }
        return new SceneObjectIdentity(true, objectId, category, shape, rotation,
                authoredPlane, anchor.x(), anchor.y(), footprintWidth, footprintLength);
    }

    /** Collision-free canonical identifier for editor/runtime maps and diagnostics. */
    public String stableId() {
        if (!present) return "none";
        return objectId + ":" + category.name() + ":" + shape + ":" + rotation + ":"
                + authoredPlane + ":" + anchorX + ":" + anchorY + ":"
                + footprintWidth + "x" + footprintLength;
    }

    /** Scene placement offset from the south-west anchor to the model centre. */
    public int centerOffsetX() {
        return footprintWidth * 64;
    }

    /** Scene placement offset from the south-west anchor to the model centre. */
    public int centerOffsetZ() {
        return footprintLength * 64;
    }
}
