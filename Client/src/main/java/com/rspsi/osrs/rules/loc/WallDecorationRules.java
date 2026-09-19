package com.rspsi.osrs.rules.loc;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;
import java.util.Optional;

/**
 * Formal OSRS rules for wall decoration placement, displacement offsets, and multi-part variants.
 */
public final class WallDecorationRules {
    public static final int DEFAULT_FALLBACK_DISPLACEMENT = 16;

    public static final int[] DECOR_DISPLACEMENT_X = {1, 0, -1, 0};
    public static final int[] DECOR_DISPLACEMENT_Z = {0, -1, 0, 1};
    public static final int[] DIAGONAL_DISPLACEMENT_X = {1, -1, -1, 1};
    public static final int[] DIAGONAL_DISPLACEMENT_Z = {-1, -1, 1, 1};

    private WallDecorationRules() {}

    /**
     * Resolves the displacement value for a wall decoration.
     * In OSRS, wall decorations inherit their displacement from the wall on the same tile;
     * if no wall is present, it falls back to 16.
     */
    public static int resolveDisplacement(
            WorldObject decoration,
            ObjectAppearanceView ownAppearance,
            WorldDocument document,
            DefinitionProvider definitions
    ) {
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(document, "document");

        if (decoration.type() < 5 || decoration.type() > 8) {
            return ownAppearance != null ? ownAppearance.decorDisplacement() : 0;
        }

        if (definitions != null) {
            for (WorldObject candidate : document.tile(decoration.plane(), decoration.x(), decoration.y())
                    .snapshot().objects()) {
                if (candidate.type() >= 0 && candidate.type() <= 3) {
                    Optional<ObjectAppearanceView> wall = definitions.objectAppearance(candidate.id());
                    if (wall.isPresent()) {
                        return wall.get().decorDisplacement();
                    }
                }
            }
        }

        return DEFAULT_FALLBACK_DISPLACEMENT;
    }

    /** Straight offset X for shape 5. */
    public static int straightOffsetX(int rotation, int displacement) {
        return DECOR_DISPLACEMENT_X[rotation & 3] * displacement;
    }

    /** Straight offset Z for shape 5. */
    public static int straightOffsetZ(int rotation, int displacement) {
        return DECOR_DISPLACEMENT_Z[rotation & 3] * displacement;
    }

    /** Diagonal offset X for shapes 6 and 8 (halved displacement). */
    public static int diagonalOffsetX(int rotation, int displacement) {
        return DIAGONAL_DISPLACEMENT_X[rotation & 3] * (displacement / 2);
    }

    /** Diagonal offset Z for shapes 6 and 8 (halved displacement). */
    public static int diagonalOffsetZ(int rotation, int displacement) {
        return DIAGONAL_DISPLACEMENT_Z[rotation & 3] * (displacement / 2);
    }
}
