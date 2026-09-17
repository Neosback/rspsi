package com.rspsi.editor.io;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionStateListener;
import com.rspsi.project.ProjectLayout;
import com.rspsi.project.ProjectMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds the neutral autosave format to one editor session.
 *
 * <p>The coordinator deliberately has no scheduler. A frontend may call
 * {@link #autosaveNow()} from its timer, while session changes trigger an
 * immediate recovery snapshot for short-lived edits. This keeps scheduling
 * policy out of the editor core and ensures undo/redo state is captured by the
 * same canonical snapshot format.</p>
 */
public final class SessionAutosaveCoordinator implements AutoCloseable {
    private final ProjectLayout layout;
    private final ProjectMetadata project;
    private final EditorSession session;
    private final SessionStateListener listener;
    private volatile IOException lastFailure;
    private boolean closed;

    /** Starts autosave observation for an initialized project layout. */
    public SessionAutosaveCoordinator(ProjectLayout layout, ProjectMetadata project,
                                      EditorSession session) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.project = Objects.requireNonNull(project, "project");
        this.session = Objects.requireNonNull(session, "session");
        this.listener = changed -> {
            try {
                // Also snapshot a clean state after undo. Otherwise a crash
                // after undo could recover an obsolete edited snapshot.
                autosaveNow();
            } catch (IOException exception) {
                lastFailure = exception;
            }
        };
        session.addStateListener(listener);
    }

    /** Writes a complete recovery snapshot without changing the saved marker. */
    public synchronized void autosaveNow() throws IOException {
        ensureOpen();
        SessionAutosaveStore.write(layout.sessionAutosaveFile(), session, project);
        lastFailure = null;
    }

    /** Returns the last write failure, if the frontend needs to surface it. */
    public synchronized Optional<IOException> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    /** Returns whether a recovery snapshot has been written for this project. */
    public boolean hasSnapshot() {
        return Files.isRegularFile(layout.sessionAutosaveFile());
    }

    /** Reads the recovery snapshot without opening or modifying a cache. */
    public SessionAutosaveStore.AutosaveSnapshot readSnapshot() throws IOException {
        ensureOpen();
        return SessionAutosaveStore.read(layout.sessionAutosaveFile());
    }

    /** Returns whether the snapshot belongs to the project identity supplied here. */
    public boolean snapshotMatchesProject() throws IOException {
        if (!hasSnapshot()) return false;
        return project.equals(readSnapshot().project());
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        session.removeStateListener(listener);
    }

    private synchronized void ensureOpen() {
        if (closed) throw new IllegalStateException("Autosave coordinator is closed");
    }
}
