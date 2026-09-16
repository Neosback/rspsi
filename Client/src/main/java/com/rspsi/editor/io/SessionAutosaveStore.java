package com.rspsi.editor.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.project.ProjectMetadata;
import com.rspsi.project.ProjectLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic, cache-independent recovery snapshots for an editor session.
 *
 * <p>The snapshot contains the complete canonical document rather than
 * partially packed cache files. It is therefore safe to write during an edit
 * session and can be recovered without opening the source cache.</p>
 */
public final class SessionAutosaveStore {
    public static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SessionAutosaveStore() {
    }

    /** Writes the canonical snapshot for an initialized project layout. */
    public static void write(ProjectLayout layout, EditorSession session) throws IOException {
        Objects.requireNonNull(layout, "layout");
        write(layout.sessionAutosaveFile(), session, layout.readMetadata());
    }

    public static void write(Path path, EditorSession session, ProjectMetadata project)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(project, "project");
        WorldDocument world = session.world();
        WorldFragment fragment = WorldFragment.capture(world,
                new TileBounds(0, 0, world.width() - 1, world.length() - 1));
        String json = GSON.toJson(new EncodedAutosave(FORMAT_VERSION, project,
                session.history().position(), fragment));
        atomicWrite(path, json);
    }

    public static AutosaveSnapshot read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("formatVersion")) {
                throw new IOException("Autosave formatVersion is missing: " + path);
            }
            int version = root.get("formatVersion").getAsInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported autosave format: " + version);
            }
            EncodedAutosave encoded = GSON.fromJson(json, EncodedAutosave.class);
            if (encoded == null || encoded.project() == null || encoded.fragment() == null) {
                throw new IOException("Autosave is incomplete: " + path);
            }
            if (encoded.historyPosition() < 0) {
                throw new IOException("Autosave history position cannot be negative: " + path);
            }
            return new AutosaveSnapshot(encoded.project(), encoded.historyPosition(),
                    restore(encoded.fragment()));
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException exception) {
            throw new IOException("Invalid autosave JSON: " + path, exception);
        }
    }

    /** Reads the canonical snapshot for an initialized project layout. */
    public static AutosaveSnapshot read(ProjectLayout layout) throws IOException {
        Objects.requireNonNull(layout, "layout");
        return read(layout.sessionAutosaveFile());
    }

    private static WorldDocument restore(WorldFragment fragment) throws IOException {
        if (fragment.bounds().minX() != 0 || fragment.bounds().minY() != 0) {
            throw new IOException("Autosave fragment must start at document origin");
        }
        int planes = fragment.terrain().stream()
                .mapToInt(TerrainTilePatch::plane)
                .max().orElse(-1) + 1;
        if (planes <= 0) {
            throw new IOException("Autosave contains no terrain planes");
        }
        WorldDocument document = new WorldDocument(fragment.bounds().width(),
                fragment.bounds().height(), planes);
        Set<String> coordinates = new HashSet<>();
        for (TerrainTilePatch patch : fragment.terrain()) {
            if (patch.plane() >= planes || patch.x() >= document.width()
                    || patch.y() >= document.length()
                    || !coordinates.add(patch.plane() + ":" + patch.x() + ":" + patch.y())) {
                throw new IOException("Autosave contains an invalid or duplicate terrain patch");
            }
            TileSnapshot snapshot = patch.snapshot();
            document.tile(patch.plane(), patch.x(), patch.y()).restore(
                    new TileSnapshot(snapshot.southWestHeight(), snapshot.southEastHeight(),
                            snapshot.northEastHeight(), snapshot.northWestHeight(),
                            snapshot.underlayId(), snapshot.overlayId(), snapshot.overlayShape(),
                            snapshot.overlayRotation(), snapshot.flags(), java.util.List.of()));
        }
        for (WorldObject object : fragment.objects()) {
            if (object.plane() >= planes || object.x() >= document.width()
                    || object.y() >= document.length()) {
                throw new IOException("Autosave object lies outside the document: " + object);
            }
            TileSnapshot before = document.tile(object.plane(), object.x(), object.y()).snapshot();
            ArrayList<WorldObject> objects = new ArrayList<>(before.objects());
            if (!objects.contains(object)) objects.add(object);
            document.tile(object.plane(), object.x(), object.y()).restore(new TileSnapshot(
                    before.southWestHeight(), before.southEastHeight(), before.northEastHeight(),
                    before.northWestHeight(), before.underlayId(), before.overlayId(),
                    before.overlayShape(), before.overlayRotation(), before.flags(), objects));
        }
        return document;
    }

    private static void atomicWrite(Path path, String json) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, json, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public record AutosaveSnapshot(ProjectMetadata project, int historyPosition,
                                   WorldDocument world) {
        public AutosaveSnapshot {
            project = Objects.requireNonNull(project, "project");
            if (historyPosition < 0) {
                throw new IllegalArgumentException("History position cannot be negative");
            }
            world = Objects.requireNonNull(world, "world");
        }
    }

    private record EncodedAutosave(int formatVersion, ProjectMetadata project,
                                   int historyPosition, WorldFragment fragment) {
    }
}
