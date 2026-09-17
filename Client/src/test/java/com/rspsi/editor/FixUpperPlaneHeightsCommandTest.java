package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixUpperPlaneHeightsCommandTest {

    @Test
    void derivesEveryUpperPlaneFromPlaneZeroAndPreservesOtherFields() {
        WorldDocument world = new WorldDocument(2, 2, 4);
        TileCoordinate coordinate = new TileCoordinate(2, 1, 1);
        TileSnapshot originalUpper = new TileSnapshot(900, 901, 902, 903,
                7, 8, 2, 3, 6,
                List.of(new com.rspsi.editor.model.WorldObject(42, 10, 1, 2, 1, 1)));
        world.tile(coordinate).restore(originalUpper);
        world.tile(0, 1, 1).restore(new TileSnapshot(100, 110, 120, 130,
                1, 2, 3, 0, 0, List.of()));

        EditorSession session = new EditorSession(world);
        session.execute(new FixUpperPlaneHeightsCommand());

        assertEquals(new TileSnapshot(-380, -370, -360, -350,
                        7, 8, 2, 3, 6, originalUpper.objects()),
                world.tile(coordinate).snapshot());
        assertEquals(-140, world.tile(1, 1, 1).snapshot().southWestHeight());
        assertEquals(-620, world.tile(3, 1, 1).snapshot().southWestHeight());
        assertEquals(1, session.history().size());
    }

    @Test
    void undoAndRedoRestoreTheCompleteHeightFixAsOneEdit() {
        WorldDocument world = new WorldDocument(2, 2, 3);
        TileCoordinate coordinate = new TileCoordinate(1, 0, 0);
        TileSnapshot before = world.tile(coordinate).snapshot();
        EditorSession session = new EditorSession(world);
        session.execute(new FixUpperPlaneHeightsCommand());
        TileSnapshot changed = world.tile(coordinate).snapshot();

        assertTrue(session.undo());
        assertEquals(before, world.tile(coordinate).snapshot());
        assertTrue(session.redo());
        assertEquals(changed, world.tile(coordinate).snapshot());
        assertEquals(1, session.history().position());
    }
}
