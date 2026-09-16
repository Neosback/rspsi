package com.rspsi.editor.render;

import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.model.WorldObject;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Renderer-neutral object projection with definition-derived scene data.
 * The original {@link WorldObject} remains embedded as the canonical edit
 * identity; this record only makes derived placement inputs explicit.
 */
public record RenderObject(
        WorldObject object,
        ObjectCategory category,
        Optional<OsrsLocShape> shape,
        int footprintWidth,
        int footprintLength,
        int[] modelIds,
        boolean blocksMovement,
        boolean blocksProjectile,
        ObjectAppearanceView appearance
) {
    /** Compatibility constructor for callers that only provide geometry. */
    public RenderObject(WorldObject object, ObjectCategory category, Optional<OsrsLocShape> shape,
                        int footprintWidth, int footprintLength, int[] modelIds,
                        boolean blocksMovement, boolean blocksProjectile) {
        this(object, category, shape, footprintWidth, footprintLength, modelIds,
                blocksMovement, blocksProjectile, ObjectAppearanceView.empty());
    }

    public RenderObject {
        object = Objects.requireNonNull(object, "object");
        category = Objects.requireNonNull(category, "category");
        shape = Objects.requireNonNull(shape, "shape");
        appearance = Objects.requireNonNull(appearance, "appearance");
        if (footprintWidth <= 0 || footprintLength <= 0) {
            throw new IllegalArgumentException("Object footprint must be positive");
        }
        modelIds = modelIds == null ? new int[0] : modelIds.clone();
        if (Arrays.stream(modelIds).anyMatch(id -> id < 0)) {
            throw new IllegalArgumentException("Object model IDs cannot be negative");
        }
    }

    @Override
    public int[] modelIds() {
        return modelIds.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RenderObject value)) return false;
        return footprintWidth == value.footprintWidth
                && footprintLength == value.footprintLength
                && blocksMovement == value.blocksMovement
                && blocksProjectile == value.blocksProjectile
                && Objects.equals(appearance, value.appearance)
                && Objects.equals(object, value.object)
                && category == value.category
                && Objects.equals(shape, value.shape)
                && Arrays.equals(modelIds, value.modelIds);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(object, category, shape, footprintWidth, footprintLength,
                blocksMovement, blocksProjectile, appearance);
        return 31 * result + Arrays.hashCode(modelIds);
    }

    @Override
    public String toString() {
        return "RenderObject[object=" + object
                + ", category=" + category
                + ", shape=" + shape
                + ", footprintWidth=" + footprintWidth
                + ", footprintLength=" + footprintLength
                + ", modelIds=" + Arrays.toString(modelIds)
                + ", blocksMovement=" + blocksMovement
                + ", blocksProjectile=" + blocksProjectile
                + ", appearance=" + appearance + ']';
    }

    /** Resolves a canonical object into renderer inputs at the neutral boundary. */
    public static RenderObject resolve(WorldObject object,
                                       ObjectDefinitionView definition,
                                       ObjectCollisionView collision) {
        return resolve(object, definition, collision, null);
    }

    /** Resolves canonical object data plus optional appearance metadata. */
    public static RenderObject resolve(WorldObject object,
                                       ObjectDefinitionView definition,
                                       ObjectCollisionView collision,
                                       ObjectAppearanceView appearance) {
        Objects.requireNonNull(object, "object");
        int width = definition == null ? collision == null ? 1 : collision.width()
                : Math.max(1, definition.width());
        int length = definition == null ? collision == null ? 1 : collision.length()
                : Math.max(1, definition.length());
        if ((object.rotation() & 1) != 0) {
            int swap = width;
            width = length;
            length = swap;
        }
        return new RenderObject(object, object.category(), object.shape(), width, length,
                definition == null ? new int[0] : definition.modelIds(),
                collision != null && collision.blockWalk() > 0,
                collision != null && collision.blockProjectile(),
                appearance == null ? ObjectAppearanceView.empty() : appearance);
    }
}
