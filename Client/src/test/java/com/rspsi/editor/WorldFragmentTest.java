package com.rspsi.editor;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldFragmentTest {

    @Test
    void captureAndPastePreserveTerrainObjectsAndAtomicUndo() {
        WorldDocument document = new WorldDocument(4, 4, 2);
        document.tile(0, 1, 1).restore(snapshot(17,
                List.of(new WorldObject(100, 10, 2, 0, 1, 1))));
        EditorSession source = new EditorSession(document);
        WorldFragment fragment = WorldFragment.capture(document, new TileBounds(1, 1, 1, 1));

        source.execute(new PasteFragmentCommand(fragment, 2, 2));

        assertEquals(17, document.tile(0, 2, 2).snapshot().underlayId());
        assertEquals(List.of(new WorldObject(100, 10, 2, 0, 2, 2)),
                document.tile(0, 2, 2).snapshot().objects());
        assertEquals(1, source.history().size());

        assertTrue(source.undo());
        assertEquals(0, document.tile(0, 2, 2).snapshot().underlayId());
        assertTrue(document.tile(0, 2, 2).snapshot().objects().isEmpty());
    }

    @Test
    void fragmentRejectsPatchesOutsideBounds() {
        assertThrows(IllegalArgumentException.class, () -> new WorldFragment(
                new TileBounds(2, 2, 2, 2),
                List.of(new com.rspsi.editor.model.TerrainTilePatch(0, 1, 2, snapshot(1, List.of()))),
                List.of()));
    }

    private static TileSnapshot snapshot(int underlay, List<WorldObject> objects) {
        return new TileSnapshot(1, 2, 3, 4, underlay, 5, 6, 1, 7, objects);
    }
}
