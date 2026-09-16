package com.rspsi.editor;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import com.rspsi.editor.selection.VertexSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectionModelTest {
    @Test
    void existingTileSelectionApiUsesUnifiedSelectionValue() {
        SelectionModel selection = new SelectionModel();
        TileCoordinate first = new TileCoordinate(0, 1, 2);
        TileCoordinate second = new TileCoordinate(0, 3, 4);

        selection.select(first);
        assertInstanceOf(TileSelection.class, selection.current());
        selection.select(second);
        assertInstanceOf(TileSetSelection.class, selection.current());
        assertEquals(2, selection.tiles().size());
    }

    @Test
    void nonTileSelectionsReplacePriorSelectionKind() {
        SelectionModel selection = new SelectionModel();

        selection.selectArea(1, new TileBounds(2, 3, 4, 5));
        assertInstanceOf(TileAreaSelection.class, selection.current());
        selection.selectVertex(new VertexSelection(1, 2, 3, 2));
        assertInstanceOf(VertexSelection.class, selection.current());
        selection.selectObject(new WorldObject(7, 10, 0, 1, 2, 3));
        assertInstanceOf(ObjectSelection.class, selection.current());
        assertTrue(selection.tiles().isEmpty());
    }
}
