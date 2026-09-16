package com.rspsi.editor.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Canonical OSRS location shapes used by map data and the editor inspector.
 *
 * <p>The category mapping follows OpenRune-Server's neutral routefinder
 * constants. In particular, roof and centrepiece shapes are ground-layer
 * locations; the shape, not a renderer-specific class, determines the layer.</p>
 */
public enum OsrsLocShape {
    WALL_STRAIGHT(0, ObjectCategory.WALL, "Straight wall"),
    WALL_DIAGONAL_CORNER(1, ObjectCategory.WALL, "Diagonal wall corner"),
    WALL_L(2, ObjectCategory.WALL, "L-shaped wall"),
    WALL_SQUARE_CORNER(3, ObjectCategory.WALL, "Square wall corner"),
    WALL_DECOR_STRAIGHT_NO_OFFSET(4, ObjectCategory.WALL_DECOR, "Straight wall decor"),
    WALL_DECOR_STRAIGHT_OFFSET(5, ObjectCategory.WALL_DECOR, "Offset wall decor"),
    WALL_DECOR_DIAGONAL_OFFSET(6, ObjectCategory.WALL_DECOR, "Diagonal offset wall decor"),
    WALL_DECOR_DIAGONAL_NO_OFFSET(7, ObjectCategory.WALL_DECOR, "Diagonal wall decor"),
    WALL_DECOR_DIAGONAL_BOTH(8, ObjectCategory.WALL_DECOR, "Two-sided diagonal wall decor"),
    WALL_DIAGONAL(9, ObjectCategory.GROUND, "Diagonal game object"),
    CENTREPIECE_STRAIGHT(10, ObjectCategory.GROUND, "Straight game object"),
    CENTREPIECE_DIAGONAL(11, ObjectCategory.GROUND, "Diagonal game object"),
    ROOF_STRAIGHT(12, ObjectCategory.GROUND, "Straight roof"),
    ROOF_DIAGONAL_WITH_ROOF_EDGE(13, ObjectCategory.GROUND, "Diagonal roof with edge"),
    ROOF_DIAGONAL(14, ObjectCategory.GROUND, "Diagonal roof"),
    ROOF_L_CONCAVE(15, ObjectCategory.GROUND, "Concave L roof"),
    ROOF_L_CONVEX(16, ObjectCategory.GROUND, "Convex L roof"),
    ROOF_FLAT(17, ObjectCategory.GROUND, "Flat roof"),
    ROOF_EDGE_STRAIGHT(18, ObjectCategory.GROUND, "Straight roof edge"),
    ROOF_EDGE_DIAGONAL_CORNER(19, ObjectCategory.GROUND, "Diagonal roof-edge corner"),
    ROOF_EDGE_L(20, ObjectCategory.GROUND, "L-shaped roof edge"),
    ROOF_EDGE_SQUARE_CORNER(21, ObjectCategory.GROUND, "Square roof-edge corner"),
    GROUND_DECOR(22, ObjectCategory.GROUND_DECOR, "Ground decor");

    private final int id;
    private final ObjectCategory category;
    private final String displayName;

    OsrsLocShape(int id, ObjectCategory category, String displayName) {
        this.id = id;
        this.category = category;
        this.displayName = displayName;
    }

    public int id() {
        return id;
    }

    public ObjectCategory category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<OsrsLocShape> fromId(int id) {
        return Arrays.stream(values()).filter(shape -> shape.id == id).findFirst();
    }
}
