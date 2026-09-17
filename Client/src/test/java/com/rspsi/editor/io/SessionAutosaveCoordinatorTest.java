package com.rspsi.editor.io;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.project.ProjectLayout;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAutosaveCoordinatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void observesEditsAndUndoWithoutChangingSavedMarker() throws Exception {
        WorldDocument document = new WorldDocument(2, 2, 1);
        EditorSession session = new EditorSession(document);
        ProjectMetadata project = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, 2, "cache-fingerprint"));
        ProjectLayout layout = new ProjectLayout(temporaryDirectory.resolve("project"));
        layout.initialize(project);

        try (SessionAutosaveCoordinator autosave =
                     new SessionAutosaveCoordinator(layout, project, session)) {
            assertFalse(autosave.hasSnapshot());
            TileSnapshot before = document.tile(0, 1, 1).snapshot();
            TileSnapshot after = new TileSnapshot(10, 20, 30, 40, 7, 8,
                    3, 1, 6, List.of());
            session.execute(new SetTileCommand(new TileCoordinate(0, 1, 1),
                    before, after, "edit"));

            assertTrue(session.isDirty());
            assertTrue(autosave.hasSnapshot());
            assertTrue(autosave.snapshotMatchesProject());
            assertEquals(after, autosave.readSnapshot().world().tile(0, 1, 1).snapshot());

            session.undo();
            assertFalse(session.isDirty());
            assertEquals(before, autosave.readSnapshot().world().tile(0, 1, 1).snapshot());
        }
    }

    @Test
    void doesNotWriteAfterClose() throws Exception {
        WorldDocument document = new WorldDocument(1, 1, 1);
        EditorSession session = new EditorSession(document);
        ProjectMetadata project = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, null, "cache-fingerprint"));
        ProjectLayout layout = new ProjectLayout(temporaryDirectory.resolve("project"));
        layout.initialize(project);
        SessionAutosaveCoordinator autosave =
                new SessionAutosaveCoordinator(layout, project, session);
        autosave.close();

        assertThrows(IllegalStateException.class, autosave::autosaveNow);
        assertFalse(autosave.hasSnapshot());
    }
}
