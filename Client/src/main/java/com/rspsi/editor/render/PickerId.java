package com.rspsi.editor.render;

/**
 * Canonical bit layout for a GPU picker id: one 32-bit integer that names exactly which tile
 * (and, for non-terrain geometry, which category slot on that tile) a rendered fragment belongs
 * to. Mirrors the proven scheme from the reference project at
 * {@code /Users/tylercovalt/Desktop/oldlostproject/src} (its {@code PickerId.java}), so the
 * eventual GLSL-side packing in the fragment/vertex shaders can be a direct line-for-line port.
 *
 * <p>Layout: {@code bit0 valid | bits1-2 plane | bits3-15 tileX(13b) | bits16-28 tileY(13b) |
 * bits29-31 slot(3b)}. 13 bits per axis covers world tile coordinates 0..8191, which is every
 * OSRS region actually in use today; a coordinate outside that range would alias, but nothing in
 * the live game world currently reaches it.</p>
 */
public final class PickerId {

    public static final int INVALID = 0;

    private static final int VALID_BIT = 1;
    private static final int PLANE_SHIFT = 1;
    private static final int TILE_X_SHIFT = 3;
    private static final int TILE_X_MASK = 0x1FFF; // 13 bits
    private static final int TILE_Y_SHIFT = 16;
    private static final int TILE_Y_MASK = 0x1FFF; // 13 bits
    private static final int SLOT_SHIFT = 29;
    private static final int SLOT_MASK = 0x7; // 3 bits
    private static final int PLANE_MASK = 0x3; // 2 bits

    private PickerId() {
    }

    /** Slot for terrain geometry; matches {@link SceneLayer.Kind#TERRAIN}'s ordinal (0). */
    public static int terrainSlot() {
        return SceneLayer.Kind.TERRAIN.ordinal();
    }

    /** Slot for a model's geometry, derived from the layer it renders in. */
    public static int slotFor(SceneLayer.Kind layer) {
        return layer.ordinal() & SLOT_MASK;
    }

    public static int encode(int plane, int tileX, int tileY, int slot) {
        return VALID_BIT
                | ((plane & PLANE_MASK) << PLANE_SHIFT)
                | ((tileX & TILE_X_MASK) << TILE_X_SHIFT)
                | ((tileY & TILE_Y_MASK) << TILE_Y_SHIFT)
                | ((slot & SLOT_MASK) << SLOT_SHIFT);
    }

    public static boolean isValid(int id) {
        return (id & VALID_BIT) != 0;
    }

    public static int plane(int id) {
        return (id >>> PLANE_SHIFT) & PLANE_MASK;
    }

    public static int tileX(int id) {
        return (id >>> TILE_X_SHIFT) & TILE_X_MASK;
    }

    public static int tileY(int id) {
        return (id >>> TILE_Y_SHIFT) & TILE_Y_MASK;
    }

    public static int slot(int id) {
        return (id >>> SLOT_SHIFT) & SLOT_MASK;
    }
}
