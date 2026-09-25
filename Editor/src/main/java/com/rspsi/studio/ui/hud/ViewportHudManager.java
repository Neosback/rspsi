package com.rspsi.studio.ui.hud;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Places viewport HUDs into managed, collision-free stacks and owns the user's
 * presentation overrides independently from the plugin/content that supplies a HUD.
 */
public final class ViewportHudManager {
    public enum Quadrant {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public record Placement(float x, float y, float width, float height) { }

    public record HudState(boolean visible, float offsetX, float offsetY, float opacity) {
        /** Compatibility shape for layouts/tests written before opacity persistence. */
        public HudState(boolean visible, float offsetX, float offsetY) {
            this(visible, offsetX, offsetY, 0.86f);
        }

        public HudState {
            if (!Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
                throw new IllegalArgumentException("HUD offsets must be finite");
            }
            if (!Float.isFinite(opacity)) {
                throw new IllegalArgumentException("HUD opacity must be finite");
            }
            opacity = clampOpacity(opacity);
        }
    }

    private static final class HudConfig {
        private Quadrant quadrant;
        private int priority;
        private boolean visible = true;
        private boolean movable = true;
        private float offsetX;
        private float offsetY;
        private float defaultOpacity = 0.86f;
        private float opacity = 0.86f;

        private HudConfig(Quadrant quadrant, int priority) {
            this.quadrant = quadrant;
            this.priority = priority;
        }
    }

    private final Map<Quadrant, Float> stackOffsets = new EnumMap<>(Quadrant.class);
    private final Map<String, HudConfig> configs = new LinkedHashMap<>();
    private float viewportX;
    private float viewportY;
    private float viewportWidth;
    private float viewportHeight;
    private float padding = 14.0f;
    private float gap = 6.0f;

    public ViewportHudManager() {
        resetStackOffsets();
    }

    public synchronized void register(String id, Quadrant quadrant, int priority) {
        register(id, quadrant, priority, true, 0.86f);
    }

    /**
     * Registers or refreshes HUD metadata. A user's opacity/offset remains stable
     * across frames; the default opacity is applied only when the HUD is first seen
     * or after Reset Workspace.
     */
    public synchronized void register(
            String id,
            Quadrant quadrant,
            int priority,
            boolean movable,
            float defaultOpacity) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quadrant, "quadrant");
        HudConfig config = configs.get(id);
        if (config == null) {
            config = new HudConfig(quadrant, priority);
            config.defaultOpacity = clampOpacity(defaultOpacity);
            config.opacity = config.defaultOpacity;
            configs.put(id, config);
        }
        config.quadrant = quadrant;
        config.priority = priority;
        config.movable = movable;
        config.defaultOpacity = clampOpacity(defaultOpacity);
    }

    public synchronized int priority(String id) {
        HudConfig config = configs.get(id);
        return config == null ? Integer.MAX_VALUE : config.priority;
    }

    public synchronized boolean isVisible(String id) {
        HudConfig config = configs.get(id);
        return config == null || config.visible;
    }

    public synchronized void setVisible(String id, boolean visible) {
        config(id).visible = visible;
    }

    public synchronized boolean isMovable(String id) {
        HudConfig config = configs.get(id);
        return config != null && config.movable;
    }

    public synchronized float opacity(String id) {
        HudConfig config = configs.get(id);
        return config == null ? 0.86f : config.opacity;
    }

    public synchronized void setOpacity(String id, float opacity) {
        config(id).opacity = clampOpacity(opacity);
    }

    public synchronized void setUserOffset(String id, float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("HUD offsets must be finite");
        }
        HudConfig config = config(id);
        config.offsetX = x;
        config.offsetY = y;
    }

    public synchronized void moveBy(String id, float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return;
        HudConfig config = config(id);
        if (!config.movable) return;
        config.offsetX += dx;
        config.offsetY += dy;
    }

    public synchronized void beginFrame(float x, float y, float width, float height) {
        viewportX = x;
        viewportY = y;
        viewportWidth = Math.max(1.0f, width);
        viewportHeight = Math.max(1.0f, height);
        resetStackOffsets();
    }

    public synchronized float viewportX() { return viewportX; }
    public synchronized float viewportY() { return viewportY; }
    public synchronized float viewportWidth() { return viewportWidth; }
    public synchronized float viewportHeight() { return viewportHeight; }

    /** Places a registered HUD. Returns null when the HUD is hidden. */
    public synchronized Placement place(String id, float width, float height) {
        HudConfig config = config(id);
        if (!config.visible) return null;
        Placement base = placeInternal(config.quadrant, width, height);
        return new Placement(
                clampX(base.x() + config.offsetX, base.width()),
                clampY(base.y() + config.offsetY, base.height()),
                base.width(),
                base.height());
    }

    /** Compatibility placement for callers not yet registered by ID. */
    public synchronized Placement place(Quadrant quadrant, float width, float height) {
        return placeInternal(quadrant, width, height);
    }

    public synchronized Map<String, HudState> snapshot() {
        Map<String, HudState> result = new LinkedHashMap<>();
        configs.forEach((id, config) -> result.put(id,
                new HudState(config.visible, config.offsetX, config.offsetY, config.opacity)));
        return Map.copyOf(result);
    }

    public synchronized void restore(Map<String, HudState> states) {
        if (states == null) return;
        states.forEach((id, state) -> {
            if (state == null) return;
            HudConfig config = config(id);
            config.visible = state.visible();
            config.offsetX = state.offsetX();
            config.offsetY = state.offsetY();
            config.opacity = clampOpacity(state.opacity());
        });
    }

    public synchronized void resetUserState() {
        configs.values().forEach(config -> {
            config.visible = true;
            config.offsetX = 0.0f;
            config.offsetY = 0.0f;
            config.opacity = config.defaultOpacity;
        });
    }

    public void setPadding(float padding) { this.padding = Math.max(0.0f, padding); }
    public void setGap(float gap) { this.gap = Math.max(0.0f, gap); }

    private HudConfig config(String id) {
        Objects.requireNonNull(id, "id");
        return configs.computeIfAbsent(id,
                ignored -> new HudConfig(Quadrant.BOTTOM_LEFT, Integer.MAX_VALUE));
    }

    private Placement placeInternal(Quadrant quadrant, float width, float height) {
        float w = Math.max(1.0f, width);
        float h = Math.max(1.0f, height);
        float offset = stackOffsets.getOrDefault(quadrant, 0.0f);
        float x = switch (quadrant) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> viewportX + padding;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> viewportX + (viewportWidth - w) * 0.5f;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> viewportX + viewportWidth - padding - w;
        };
        float y = switch (quadrant) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> viewportY + padding + offset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT ->
                    viewportY + (viewportHeight - h) * 0.5f + offset;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT ->
                    viewportY + viewportHeight - padding - h - offset;
        };
        stackOffsets.put(quadrant, offset + h + gap);
        return new Placement(x, y, w, h);
    }

    private float clampX(float x, float width) {
        float min = viewportX;
        float max = Math.max(min, viewportX + viewportWidth - width);
        return Math.max(min, Math.min(max, x));
    }

    private float clampY(float y, float height) {
        float min = viewportY;
        float max = Math.max(min, viewportY + viewportHeight - height);
        return Math.max(min, Math.min(max, y));
    }

    private static float clampOpacity(float value) {
        return Math.max(0.15f, Math.min(1.0f, value));
    }

    private void resetStackOffsets() {
        for (Quadrant quadrant : Quadrant.values()) stackOffsets.put(quadrant, 0.0f);
    }
}
