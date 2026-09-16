package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Set;

/** Receives the affected tiles after a command, undo, or redo completes. */
@FunctionalInterface
public interface SessionChangeListener {
    void changed(Set<TileCoordinate> tiles);
}
