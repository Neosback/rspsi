package com.rspsi.studio.ui;

import com.rspsi.editor.model.ObjectCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mutable, in-memory style settings for the selection overlay (object hulls
 * and tile highlights), shared between {@link SelectionOverlayPlugin} (which
 * owns the settings UI) and {@link ViewportOverlayDraw} (which draws with
 * whatever these values currently are). A plain shared holder, rather than
 * plumbing settings through {@code OverlayDraw}/{@code BoxSelectTool}, since
 * those are Client-module types that must stay unaware of Studio's plugin
 * settings - the tool only ever asks for "the default tile outline," and
 * this is where that default now lives instead of a hardcoded constant.
 */
public final class SelectionOverlayStyle {

    private static final SelectionOverlayStyle SHARED = new SelectionOverlayStyle();

    public static SelectionOverlayStyle shared() {
        return SHARED;
    }

    private boolean objectHullEnabled = true;
    private boolean showObjectInfo = false;

    private final Map<ObjectCategory, Integer> outlineColors = new EnumMap<>(Map.of(
            ObjectCategory.WALL, 0xF59E0BFF,
            ObjectCategory.WALL_DECOR, 0x22D3EEFF,
            ObjectCategory.GROUND, 0xF59E0BFF,
            ObjectCategory.GROUND_DECOR, 0xA78BFAFF,
            ObjectCategory.UNKNOWN, 0x94A3B8FF));

    /** 0..255. Applied as the fill alpha of the outline color's own RGB - not a separate color. */
    private int fillAlpha = 128;
    private float outlineThickness = 2.0f;
    private boolean paintedEdge = true;

    private int tileOutlineColor = 0x40E0D0FF;
    private int tileFillAlpha = 40;

    public boolean objectHullEnabled() { return objectHullEnabled; }
    public void setObjectHullEnabled(boolean value) { objectHullEnabled = value; }

    public boolean showObjectInfo() { return showObjectInfo; }
    public void setShowObjectInfo(boolean value) { showObjectInfo = value; }

    public int outlineColor(ObjectCategory category) {
        return outlineColors.getOrDefault(category, outlineColors.get(ObjectCategory.UNKNOWN));
    }

    public void setOutlineColor(ObjectCategory category, int rgba) {
        outlineColors.put(category, rgba);
    }

    public int fillAlpha() { return fillAlpha; }
    public void setFillAlpha(int alpha) { fillAlpha = Math.max(0, Math.min(255, alpha)); }

    public float outlineThickness() { return outlineThickness; }
    public void setOutlineThickness(float thickness) { outlineThickness = Math.max(0.5f, thickness); }

    public boolean paintedEdge() { return paintedEdge; }
    public void setPaintedEdge(boolean value) { paintedEdge = value; }

    public int tileOutlineColor() { return tileOutlineColor; }
    public void setTileOutlineColor(int rgba) { tileOutlineColor = rgba; }

    public int tileFillAlpha() { return tileFillAlpha; }
    public void setTileFillAlpha(int alpha) { tileFillAlpha = Math.max(0, Math.min(255, alpha)); }

    /** Fills a category's outline color with the configured fill alpha in place of its own alpha. */
    public int fillColor(ObjectCategory category) {
        int outline = outlineColor(category);
        return (outline & 0xFFFFFF00) | fillAlpha;
    }

    public void resetToDefaults() {
        objectHullEnabled = true;
        showObjectInfo = false;
        outlineColors.put(ObjectCategory.WALL, 0xF59E0BFF);
        outlineColors.put(ObjectCategory.WALL_DECOR, 0x22D3EEFF);
        outlineColors.put(ObjectCategory.GROUND, 0xF59E0BFF);
        outlineColors.put(ObjectCategory.GROUND_DECOR, 0xA78BFAFF);
        outlineColors.put(ObjectCategory.UNKNOWN, 0x94A3B8FF);
        fillAlpha = 128;
        outlineThickness = 2.0f;
        paintedEdge = true;
        tileOutlineColor = 0x40E0D0FF;
        tileFillAlpha = 40;
    }
}
