package com.rspsi.editor.io;

import com.rspsi.cache.OsrsCacheMetadata;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SetTileCommand;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.project.ProjectMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAutosaveStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recoversCanonicalTerrainFlagsAndObjects() throws Exception {
        WorldDocument document = new WorldDocument(3, 2, 2);
        EditorSession session = new EditorSession(document);
        WorldObject object = new WorldObject(123, 10, 2, 1, 2, 1);
        TileSnapshot edited = new TileSnapshot(16, 24, 32, 40, 7, 8, 3, 1, 6,
                List.of(object));
        session.execute(new SetTileCommand(new TileCoordinate(1, 2, 1),
                document.tile(1, 2, 1).snapshot(), edited, "autosave edit"));
        ProjectMetadata project = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, 2, "cache-fingerprint"));
        Path file = temporaryDirectory.resolve("autosave/session.json");

        SessionAutosaveStore.write(file, session, project);
        SessionAutosaveStore.AutosaveSnapshot recovered = SessionAutosaveStore.read(file);

        assertEquals(project, recovered.project());
        assertEquals(1, recovered.historyPosition());
        assertEquals(edited, recovered.world().tile(1, 2, 1).snapshot());
        assertTrue(Files.exists(file));
    }

    @Test
    void replacesAutosaveAtomicallyWithoutTemporaryFiles() throws Exception {
        WorldDocument document = new WorldDocument(1, 1, 1);
        EditorSession session = new EditorSession(document);
        ProjectMetadata project = ProjectMetadata.forCache(
                new OsrsCacheMetadata(240, null, "cache-fingerprint"));
        Path file = temporaryDirectory.resolve("session.json");

        SessionAutosaveStore.write(file, session, project);
        SessionAutosaveStore.write(file, session, project);

        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }
}
