package com.rspsi.editor.tool.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Single authoritative state container for the composite Tile Painter. */
public final class TilePainterState {
    public interface Listener { void changed(TilePainterState state); }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean applyUnderlay;
    private int underlayId;
    private boolean applyOverlay = true;
    private int overlayId = 1;
    private boolean applyShape;
    private int shape;
    private boolean applyRotation;
    private int rotation;
    private boolean applyFlags;
    private int flags;
    private boolean applyHeight;
    private int height;
    private String brushId = "square";
    private int brushRadius;

    public boolean applyUnderlay() { return applyUnderlay; }
    public int underlayId() { return underlayId; }
    public boolean applyOverlay() { return applyOverlay; }
    public int overlayId() { return overlayId; }
    public boolean applyShape() { return applyShape; }
    public int shape() { return shape; }
    public boolean applyRotation() { return applyRotation; }
    public int rotation() { return rotation; }
    public boolean applyFlags() { return applyFlags; }
    public int flags() { return flags; }
    public boolean applyHeight() { return applyHeight; }
    public int height() { return height; }
    public String brushId() { return brushId; }
    public int brushRadius() { return brushRadius; }

    public void setApplyUnderlay(boolean v) { applyUnderlay = v; fire(); }
    public void setUnderlayId(int v) { underlayId = Math.max(0, v); fire(); }
    public void setApplyOverlay(boolean v) { applyOverlay = v; fire(); }
    public void setOverlayId(int v) { overlayId = Math.max(0, v); fire(); }
    public void setApplyShape(boolean v) { applyShape = v; fire(); }
    public void setShape(int v) {
        if (v < 0 || v > 11) throw new IllegalArgumentException("shape must be 0..11");
        shape = v; fire();
    }
    public void setApplyRotation(boolean v) { applyRotation = v; fire(); }
    public void setRotation(int v) {
        if (v < 0 || v > 3) throw new IllegalArgumentException("rotation must be 0..3");
        rotation = v; fire();
    }
    public void setApplyFlags(boolean v) { applyFlags = v; fire(); }
    public void setFlags(int v) { flags = v; fire(); }
    public void setApplyHeight(boolean v) { applyHeight = v; fire(); }
    public void setHeight(int v) { height = v; fire(); }
    public void setBrushId(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("brush id cannot be blank");
        brushId = v; fire();
    }
    public void setBrushRadius(int v) {
        if (v < 0 || v > 64) throw new IllegalArgumentException("brush radius must be 0..64");
        brushRadius = v; fire();
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }

    private void fire() {
        for (Listener listener : listeners) listener.changed(this);
    }
}
