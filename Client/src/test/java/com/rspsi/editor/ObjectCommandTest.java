package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObjectCommandTest {
    @Test
    void placeRotateMoveDeleteAllRoundTripThroughHistory() {
        WorldDocument world = new WorldDocument(5, 5);
        EditorSession session = new EditorSession(world);
        WorldObject object = new WorldObject(42, 10, 0, 0, 1, 1);

        session.execute(new PlaceObjectCommand(object));
        session.execute(new RotateObjectCommand(object, 2));
        WorldObject rotated = new WorldObject(42, 10, 2, 0, 1, 1);
        session.execute(new MoveObjectCommand(rotated, 3, 3));
        WorldObject moved = new WorldObject(42, 10, 2, 0, 3, 3);
        session.execute(new DeleteObjectCommand(moved));

        assertTrue(world.tile(new TileCoordinate(0, 3, 3)).snapshot().objects().isEmpty());
        assertEquals(4, session.history().size());
        assertTrue(session.undo());
        assertEquals(List.of(moved), world.tile(0, 3, 3).snapshot().objects());
        assertTrue(session.undo());
        assertEquals(List.of(rotated), world.tile(0, 1, 1).snapshot().objects());
        assertTrue(world.tile(0, 3, 3).snapshot().objects().isEmpty());
    }

    @Test
    void changedTilesIncludeBothSidesOfMove() {
        WorldObject object = new WorldObject(1, 10, 0, 0, 1, 2);
        MoveObjectCommand command = new MoveObjectCommand(object, 4, 5);

        assertEquals(java.util.Set.of(new TileCoordinate(0, 1, 2), new TileCoordinate(0, 4, 5)),
                command.changedTiles());
    }
}
