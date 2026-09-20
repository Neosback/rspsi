package com.rspsi.editor.render;

import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldWindow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Single renderer-neutral navigation service for world-tile jumps and history.
 *
 * <p>Panels must not calculate camera world positions independently. They
 * submit a {@link WorldTile} here, which keeps minimap, world-map, search and
 * future similarity/content results on one navigation contract.</p>
 */
public final class NavigationService {
    private final ViewportController viewport;
    private final Deque<WorldTile> back = new ArrayDeque<>();
    private final Deque<WorldTile> forward = new ArrayDeque<>();
    private WorldTile current;
    private int historyLimit = 100;

    public NavigationService(ViewportController viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
    }

    public ViewportController viewport() {
        return viewport;
    }

    public Optional<WorldTile> current() {
        return Optional.ofNullable(current);
    }

    public boolean canBack() {
        return !back.isEmpty();
    }

    public boolean canForward() {
        return !forward.isEmpty();
    }

    public void setHistoryLimit(int historyLimit) {
        if (historyLimit < 1) throw new IllegalArgumentException("historyLimit must be positive");
        this.historyLimit = historyLimit;
        trim(back);
        trim(forward);
    }

    public void jumpTo(WorldTile target) {
        jumpTo(target, true);
    }

    public void jumpTo(WorldTile target, boolean recordHistory) {
        Objects.requireNonNull(target, "target");
        if (recordHistory && current != null && !current.equals(target)) {
            back.addLast(current);
            trim(back);
            forward.clear();
        }
        current = target;
        frame(target);
    }

    public void jumpToLocal(LocalTile tile, WorldWindow window) {
        Objects.requireNonNull(tile, "tile");
        Objects.requireNonNull(window, "window");
        jumpTo(window.toWorld(tile));
    }

    public void jumpToRegion(int regionX, int regionY, int plane) {
        if (regionX < 0 || regionX > 255 || regionY < 0 || regionY > 255) {
            throw new IllegalArgumentException("OSRS region coordinates must be in [0,255]");
        }
        jumpTo(new WorldTile(plane, regionX * 64 + 32, regionY * 64 + 32));
    }

    public boolean back() {
        if (back.isEmpty()) return false;
        if (current != null) {
            forward.addLast(current);
            trim(forward);
        }
        current = back.removeLast();
        frame(current);
        return true;
    }

    public boolean forward() {
        if (forward.isEmpty()) return false;
        if (current != null) {
            back.addLast(current);
            trim(back);
        }
        current = forward.removeLast();
        frame(current);
        return true;
    }

    /**
     * Records the current camera tile as navigation history before a panel
     * performs a jump. Useful after free-flight camera movement.
     */
    public void synchronizeFromCamera(int plane) {
        CameraState camera = viewport.camera();
        WorldTile cameraTile = new WorldTile(
                Math.max(0, plane),
                Math.max(0, (int) Math.floor(camera.x() / 128.0f)),
                Math.max(0, (int) Math.floor(camera.z() / 128.0f)));
        if (current == null) current = cameraTile;
        else if (!current.equals(cameraTile)) {
            back.addLast(current);
            trim(back);
            current = cameraTile;
            forward.clear();
        }
    }

    private void frame(WorldTile target) {
        viewport.frameSelection(
                target.x() * 128.0f + 64.0f,
                0.0f,
                target.y() * 128.0f + 64.0f);
    }

    private void trim(Deque<WorldTile> values) {
        while (values.size() > historyLimit) values.removeFirst();
    }
}
