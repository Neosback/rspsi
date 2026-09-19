package com.rspsi.osrs.rules.loc;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.OsrsLocShape;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Formal catalog of all 23 OSRS location shapes (0 to 22) represented as explicit data descriptors.
 */
public final class LocShapeCatalog {

    public record LocShapeDescriptor(
            int id,
            String name,
            ObjectCategory category,
            boolean isWall,
            boolean isWallDecor,
            boolean isGround,
            boolean isGroundDecor,
            boolean isRoof,
            int variantCount,
            boolean hasDisplacement,
            boolean castsTerrainShadow,
            boolean supportsCollision
    ) {
        public LocShapeDescriptor {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(category, "category");
        }
    }

    private static final Map<Integer, LocShapeDescriptor> CATALOG;

    static {
        List<LocShapeDescriptor> descriptors = List.of(
                // Walls (0..3)
                new LocShapeDescriptor(0, "Straight Wall", ObjectCategory.WALL, true, false, false, false, false, 1, false, true, true),
                new LocShapeDescriptor(1, "Diagonal Wall Corner", ObjectCategory.WALL, true, false, false, false, false, 1, false, true, true),
                new LocShapeDescriptor(2, "L-Shaped Wall", ObjectCategory.WALL, true, false, false, false, false, 2, false, false, true),
                new LocShapeDescriptor(3, "Square Wall Corner", ObjectCategory.WALL, true, false, false, false, false, 1, false, true, true),

                // Wall Decorations (4..8)
                new LocShapeDescriptor(4, "Straight Wall Decor (No Offset)", ObjectCategory.WALL_DECOR, false, true, false, false, false, 1, false, false, false),
                new LocShapeDescriptor(5, "Straight Wall Decor (Offset)", ObjectCategory.WALL_DECOR, false, true, false, false, false, 1, true, false, false),
                new LocShapeDescriptor(6, "Diagonal Wall Decor (Offset)", ObjectCategory.WALL_DECOR, false, true, false, false, false, 1, true, false, false),
                new LocShapeDescriptor(7, "Diagonal Wall Decor (No Offset)", ObjectCategory.WALL_DECOR, false, true, false, false, false, 1, false, false, false),
                new LocShapeDescriptor(8, "Two-Sided Diagonal Wall Decor", ObjectCategory.WALL_DECOR, false, true, false, false, false, 2, true, false, false),

                // Diagonal Wall (9) & Game Objects (10..11)
                new LocShapeDescriptor(9, "Diagonal Game Object / Wall", ObjectCategory.GROUND, false, false, true, false, false, 1, false, false, true),
                new LocShapeDescriptor(10, "Straight Game Object", ObjectCategory.GROUND, false, false, true, false, false, 1, false, false, true),
                new LocShapeDescriptor(11, "Diagonal Game Object", ObjectCategory.GROUND, false, false, true, false, false, 1, false, false, true),

                // Roofs (12..21)
                new LocShapeDescriptor(12, "Straight Roof", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(13, "Diagonal Roof With Edge", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(14, "Diagonal Roof", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(15, "Concave L Roof", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(16, "Convex L Roof", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(17, "Flat Roof", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(18, "Straight Roof Edge", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(19, "Diagonal Roof Edge Corner", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(20, "L-Shaped Roof Edge", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),
                new LocShapeDescriptor(21, "Square Roof Edge Corner", ObjectCategory.GROUND, false, false, true, false, true, 1, false, false, false),

                // Ground Decor (22)
                new LocShapeDescriptor(22, "Ground Decor", ObjectCategory.GROUND_DECOR, false, false, false, true, false, 1, false, false, false)
        );

        CATALOG = descriptors.stream()
                .collect(Collectors.toUnmodifiableMap(LocShapeDescriptor::id, Function.identity()));
    }

    private LocShapeCatalog() {}

    /** Returns the shape descriptor for the given OSRS location shape ID (0..22). */
    public static Optional<LocShapeDescriptor> get(int shapeId) {
        return Optional.ofNullable(CATALOG.get(shapeId));
    }

    /** Returns all 23 shape descriptors in canonical ID order. */
    public static List<LocShapeDescriptor> all() {
        return List.copyOf(CATALOG.values());
    }
}
