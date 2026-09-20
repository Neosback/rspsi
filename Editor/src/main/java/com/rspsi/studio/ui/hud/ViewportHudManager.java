package com.rspsi.studio.ui.hud;

import java.util.EnumMap;
import java.util.Map;

/**
 * Places viewport HUDs into four managed stacks. HUDs request a slot each
 * frame and never position themselves with arbitrary viewport coordinates.
 */
public final class ViewportHudManager {
    public enum Quadrant { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public record Placement(float x, float y, float width, float height) {}

    private final Map<Quadrant, Float> offsets = new EnumMap<>(Quadrant.class);
    private float viewportX;
    private float viewportY;
    private float viewportWidth;
    private float viewportHeight;
    private float padding = 14.0f;
    private float gap = 6.0f;

    public ViewportHudManager() {
        resetOffsets();
    }

    public void beginFrame(float x, float y, float width, float height) {
        viewportX = x;
        viewportY = y;
        viewportWidth = Math.max(1.0f, width);
        viewportHeight = Math.max(1.0f, height);
        resetOffsets();
    }

    public Placement place(Quadrant quadrant, float width, float height) {
        float w = Math.max(1.0f, width);
        float h = Math.max(1.0f, height);
        float offset = offsets.getOrDefault(quadrant, 0.0f);
        float x = switch (quadrant) {
            case TOP_LEFT, BOTTOM_LEFT -> viewportX + padding;
            case TOP_RIGHT, BOTTOM_RIGHT -> viewportX + viewportWidth - padding - w;
        };
        float y = switch (quadrant) {
            case TOP_LEFT, TOP_RIGHT -> viewportY + padding + offset;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> viewportY + viewportHeight - padding - h - offset;
        };
        offsets.put(quadrant, offset + h + gap);
        return new Placement(x, y, w, h);
    }

    public void setPadding(float padding) {
        this.padding = Math.max(0.0f, padding);
    }

    public void setGap(float gap) {
        this.gap = Math.max(0.0f, gap);
    }

    private void resetOffsets() {
        for (Quadrant quadrant : Quadrant.values()) offsets.put(quadrant, 0.0f);
    }
}
