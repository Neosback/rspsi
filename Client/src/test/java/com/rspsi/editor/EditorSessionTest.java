package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorSessionTest {
    private static final TileCoordinate TILE = new TileCoordinate(0, 1, 2);

    @Test
    void commandHistorySupportsUndoRedoAndSavedState() {
        WorldModel world = new WorldModel(4, 4);
        EditorSession session = new EditorSession(world);
        TileSnapshot before = world.tile(TILE).snapshot();
        TileSnapshot after = new TileSnapshot(10, 20, 30, 40, 3, 4, 12, 2, 8, List.of());

        session.markSaved();
        session.execute(new SetTileCommand(TILE, before, after, "Paint tile"));

        assertTrue(session.isDirty());
        assertEquals(after, world.tile(TILE).snapshot());
        assertTrue(session.undo());
        assertFalse(session.isDirty());
        assertEquals(before, world.tile(TILE).snapshot());
        assertTrue(session.redo());
        assertTrue(session.isDirty());
        assertEquals(after, world.tile(TILE).snapshot());
    }

    @Test
    void compositeCommandIsOneHistoryEntryAndUndoesInReverse() {
        WorldModel world = new WorldModel(4, 4);
        EditorSession session = new EditorSession(world);
        TileCoordinate first = new TileCoordinate(0, 0, 0);
        TileCoordinate second = new TileCoordinate(0, 0, 1);
        TileSnapshot firstAfter = new TileSnapshot(1, 0, 0, 0, 1, 0, 0, 0, 0, List.of());
        TileSnapshot secondAfter = new TileSnapshot(2, 0, 0, 0, 2, 0, 0, 0, 0, List.of());

        session.execute(new CompositeEditCommand("Paint brush", List.of(
                new SetTileCommand(first, world.tile(first).snapshot(), firstAfter, "first"),
                new SetTileCommand(second, world.tile(second).snapshot(), secondAfter, "second"))));

        assertEquals(1, session.history().size());
        assertEquals(firstAfter, world.tile(first).snapshot());
        assertEquals(secondAfter, world.tile(second).snapshot());
        assertTrue(session.undo());
        assertEquals(0, session.history().position());
        assertEquals(0, world.tile(first).snapshot().underlayId());
        assertEquals(0, world.tile(second).snapshot().underlayId());
    }

    @Test
    void compositeCommandRollsBackEarlierEditsWhenAChildFails() {
        WorldModel world = new WorldModel(4, 4);
        EditorSession session = new EditorSession(world);
        TileCoordinate coordinate = new TileCoordinate(0, 1, 1);
        TileSnapshot before = world.tile(coordinate).snapshot();
        TileSnapshot changed = new TileSnapshot(0, 0, 0, 0, 9, 0, 0, 0, 0, List.of());

        assertThrows(IllegalStateException.class, () -> session.execute(new CompositeEditCommand("atomic", List.of(
                new SetTileCommand(coordinate, before, changed, "first"),
                new FailingCommand()))));

        assertEquals(before, world.tile(coordinate).snapshot());
        assertEquals(0, session.history().size());
        assertFalse(session.isDirty());
    }

    @Test
    void commandTransactionRollsBackDelegatesWhenAGroupedApplyFails() {
        WorldModel world = new WorldModel(4, 4);
        EditorSession session = new EditorSession(world);
        TileCoordinate coordinate = new TileCoordinate(0, 2, 2);
        TileSnapshot before = world.tile(coordinate).snapshot();
        TileSnapshot changed = new TileSnapshot(0, 0, 0, 0, 11, 0, 0, 0, 0, List.of());
        List<EditorCommand> commands = List.of(
                new SetTileCommand(coordinate, before, changed, "first"),
                new FailingCommand());

        assertThrows(IllegalStateException.class, () -> CommandTransaction.apply(commands, session));

        assertEquals(before, world.tile(coordinate).snapshot());
    }

    @Test
    void executingAfterUndoDropsRedoBranch() {
        WorldModel world = new WorldModel(2, 2);
        EditorSession session = new EditorSession(world);
        TileSnapshot initial = world.tile(0, 0, 0).snapshot();
        TileSnapshot first = new TileSnapshot(1, 0, 0, 0, 1, 0, 0, 0, 0, List.of());
        TileSnapshot replacement = new TileSnapshot(2, 0, 0, 0, 2, 0, 0, 0, 0, List.of());

        session.execute(new SetTileCommand(new TileCoordinate(0, 0, 0), initial, first, "first"));
        assertTrue(session.undo());
        session.execute(new SetTileCommand(new TileCoordinate(0, 0, 0), initial, replacement, "replacement"));

        assertFalse(session.redo());
        assertEquals(replacement, world.tile(0, 0, 0).snapshot());
    }

    @Test
    void stateListenersTrackEditsHistoryAndSaveMarker() {
        WorldModel world = new WorldModel(2, 2);
        EditorSession session = new EditorSession(world);
        java.util.List<String> states = new java.util.ArrayList<>();
        session.addStateListener(value -> states.add(value.isDirty() + ":" + value.history().position()));
        TileCoordinate coordinate = new TileCoordinate(0, 0, 0);
        TileSnapshot initial = world.tile(coordinate).snapshot();
        TileSnapshot changed = new TileSnapshot(8, 0, 0, 0, 1, 0, 0, 0, 0, List.of());

        session.execute(new SetTileCommand(coordinate, initial, changed, "height"));
        session.undo();
        session.redo();
        session.markSaved();

        assertEquals(List.of("true:1", "false:0", "true:1", "false:1"), states);
    }

    private static final class FailingCommand implements EditorCommand {
        @Override public void apply(EditorSession session) { throw new IllegalStateException("expected test failure"); }
        @Override public void undo(EditorSession session) { }
        @Override public String description() { return "failure"; }
    }
}
