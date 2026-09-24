package com.rspsi.editor.render;

/**
 * Canonical 32-bit GPU picker key for one world tile/category family.
 *
 * <p>The cache/client coordinate domain is 14 bits per world axis. RuneLite's
 * {@code WorldPoint.fromCoord} decodes Jagex coordinates with {@code 0x3FFF}
 * masks, and the bundled source contains live/mirrored coordinates above 8191.
 * A picker key therefore cannot spend bits on both a plane and a three-bit
 * category slot without aliasing valid OSRS world coordinates.</p>
 *
 * <p>Layout: {@code bit0 valid | bits1-14 tileX(14b) |
 * bits15-28 tileY(14b) | bits29-31 slot(3b)}. Plane is intentionally not
 * encoded. Plane-restricted GPU passes filter commands before rasterization;
 * unrestricted picks resolve the exact plane/object through the canonical DDA
 * picker after this key narrows the hit to a tile/category family.</p>
 */
public final class PickerId {

    public static final int INVALID = 0;

    private static final int VALID_BIT = 1;
    private static final int TILE_X_SHIFT = 1;
    private static final int TILE_X_MASK = 0x3FFF; // Jagex 14-bit world coordinate
    private static final int TILE_Y_SHIFT = 15;
    private static final int TILE_Y_MASK = 0x3FFF; // Jagex 14-bit world coordinate
    private static final int SLOT_SHIFT = 29;
    private static final int SLOT_MASK = 0x7;

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

    /**
     * Compatibility entry point for vertex metadata that still carries plane.
     * Plane is validated but deliberately omitted from the packed 32-bit key.
     */
    public static int encode(int plane, int tileX, int tileY, int slot) {
        if (plane < 0 || plane > 3) {
            throw new IllegalArgumentException("Picker plane must be within 0..3");
        }
        return encode(tileX, tileY, slot);
    }

    public static int encode(int tileX, int tileY, int slot) {
        if (tileX < 0 || tileX > TILE_X_MASK
                || tileY < 0 || tileY > TILE_Y_MASK) {
            throw new IllegalArgumentException(
                    "Picker world coordinates must fit the Jagex 14-bit domain");
        }
        if (slot < 0 || slot > SLOT_MASK) {
            throw new IllegalArgumentException("Picker slot must be within 0..7");
        }
        return VALID_BIT
                | (tileX << TILE_X_SHIFT)
                | (tileY << TILE_Y_SHIFT)
                | (slot << SLOT_SHIFT);
    }

    public static boolean isValid(int id) {
        return (id & VALID_BIT) != 0;
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
