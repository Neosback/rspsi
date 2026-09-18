package com.rspsi.editor.model;

/** Backend-neutral object placement used by editor operations. */
public record WorldObject(int id, int type, int rotation, int plane, int x, int y) {
    public WorldObject {
        if (id < 0 || type < 0 || plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("World object values cannot be negative");
        }
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("Object rotation must be between 0 and 3");
        }
    }

    /** Returns the neutral scene category implied by this object's OSRS shape. */
    public ObjectCategory category() {
        return ObjectCategory.fromType(type);
    }

    /** Returns the semantic shape when the OSRS type is supported. */
    public java.util.Optional<OsrsLocShape> shape() {
        return OsrsLocShape.fromId(type);
    }

    /**
     * RuneLite {@code WallObject.getOrientationA()} semantics for this
     * location. Cardinal wall shapes use 1/2/4/8 for west/north/east/south;
     * diagonal wall shapes use 16/32/64/128 for north-west/north-east/
     * south-east/south-west. Non-wall locations have no wall orientation.
     */
    public int wallOrientationA() {
        return switch (type) {
            case 0, 2 -> 1 << rotation;
            case 1, 3 -> 1 << (rotation + 4);
            default -> 0;
        };
    }

    /** RuneLite {@code WallObject.getOrientationB()} for the second L-wall. */
    public int wallOrientationB() {
        return type == 2 ? 1 << ((rotation + 1) & 3) : 0;
    }
}
