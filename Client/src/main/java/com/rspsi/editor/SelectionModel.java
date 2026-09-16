package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.FragmentSelection;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.selection.VertexSelection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;

/** Editor selection state kept separate from scene rendering state. */
public final class SelectionModel {
    private final Set<TileCoordinate> tiles = new LinkedHashSet<>();
    private Selection current;

    public void select(TileCoordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        tiles.add(coordinate);
        current = tiles.size() == 1
                ? new TileSelection(coordinate)
                : new TileSetSelection(tiles);
    }

    public void deselect(TileCoordinate coordinate) {
        tiles.remove(coordinate);
        current = tiles.isEmpty() ? null
                : tiles.size() == 1 ? new TileSelection(tiles.iterator().next()) : new TileSetSelection(tiles);
    }

    public void clear() {
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

    public void selectArea(int plane, TileBounds bounds) {
        clear();
        current = new TileAreaSelection(plane, bounds);
    }

    public void selectVertex(VertexSelection vertex) {
        clear();
        current = Objects.requireNonNull(vertex, "vertex");
    }

    public void selectObject(WorldObject object) {
        clear();
        current = new ObjectSelection(object);
    }

    public void selectFragment(WorldFragment fragment) {
        clear();
        current = new FragmentSelection(fragment);
    }
}
