package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.FragmentSelection;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.selection.VertexSelection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Editor selection state kept separate from scene rendering state. */
public final class SelectionModel {
    private final Set<TileCoordinate> tiles = new LinkedHashSet<>();
    private final java.util.List<SelectionChangeListener> listeners = new CopyOnWriteArrayList<>();
    private Selection current;

    public void select(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        tiles.add(coordinate);
        current = tiles.size() == 1
                ? new TileSelection(coordinate)
                : new TileSetSelection(tiles);
        notifyChanged();
    }

    public void deselect(TileCoordinate coordinate) {
        tiles.remove(coordinate);
        current = tiles.isEmpty() ? null
                : tiles.size() == 1 ? new TileSelection(tiles.iterator().next()) : new TileSetSelection(tiles);
        notifyChanged();
    }

    public void clear() {
        clearInternal();
        notifyChanged();
    }

    private void clearInternal() {
        tiles.clear();
        current = null;
    }

    public boolean contains(TileCoordinate coordinate) {
        return tiles.contains(coordinate);
    }

    public Set<TileCoordinate> tiles() {
        return Collections.unmodifiableSet(tiles);
    }

    public Selection current() {
        return current;
    }

    public void addChangeListener(SelectionChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(SelectionChangeListener listener) {
        listeners.remove(listener);
    }

    public void selectArea(int plane, TileBounds bounds) {
        clearInternal();
        current = new TileAreaSelection(plane, bounds);
        notifyChanged();
    }

    public void selectVertex(VertexSelection vertex) {
        clearInternal();
        current = Objects.requireNonNull(vertex, "vertex");
        notifyChanged();
    }

    public void selectObject(WorldObject object) {
        clearInternal();
        current = new ObjectSelection(object);
        notifyChanged();
    }

    public void selectObjects(Set<WorldObject> objects) {
        clearInternal();
        if (objects == null || objects.isEmpty()) {
            notifyChanged();
            return;
        }
        current = objects.size() == 1
                ? new ObjectSelection(objects.iterator().next())
                : new ObjectSetSelection(objects);
        notifyChanged();
    }

    public void selectFragment(WorldFragment fragment) {
        clearInternal();
        current = new FragmentSelection(fragment);
        notifyChanged();
    }

    private void notifyChanged() {
        for (SelectionChangeListener listener : listeners) {
            listener.changed(current);
        }
    }
}
