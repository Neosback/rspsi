package com.rspsi.editor.plugin;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;

/**
 * Read-only access to the latest derived scene owned by the host.
 *
 * <p>Plugins use this instead of constructing their own terrain, bridge, or
 * collision interpretation from the authored document.</p>
 */
@FunctionalInterface
public interface EditorSceneAccess {
    EditorSceneSnapshot snapshot();

    default EditorSceneSnapshot checkedSnapshot() {
        return Objects.requireNonNull(snapshot(), "scene snapshot");
    }

    default boolean contains(TileCoordinate coordinate) {
        return checkedSnapshot().contains(Objects.requireNonNull(coordinate, "coordinate"));
    }
}
