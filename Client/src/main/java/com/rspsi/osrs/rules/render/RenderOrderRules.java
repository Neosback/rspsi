package com.rspsi.osrs.rules.render;

/**
 * Formal OSRS render layer order and priority clamping rules.
 */
public final class RenderOrderRules {
    /** Standard OSRS layer rendering sequence. */
    public enum Layer {
        TERRAIN(0),
        FLOOR_DECOR(1),
        WALL(2),
        WALL_DECOR(3),
        GROUND_OBJECT(4),
        ROOF(5);

        private final int order;

        Layer(int order) {
            this.order = order;
        }

        public int order() {
            return order;
        }
    }

    private RenderOrderRules() {}

    /** Clamps priority into the valid 0..255 byte range. */
    public static int clampPriority(int priority) {
        return Math.max(0, Math.min(255, priority));
    }
}
