package com.rspsi.editor.render;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.TileCoordinate;

import java.util.Objects;
import java.util.Set;

/**
 * UI-neutral binding between one editor session and one scene renderer.
 *
 * <p>The session remains the source of truth. This controller only owns the
 * current derived scene and translates the session's 8x8 dirty queue into
 * renderer updates.</p>
 */
public final class SessionSceneController implements AutoCloseable {
    private final EditorSession session;
    private final SceneRenderer renderer;
    private final RenderSceneBuilder scenes;
    private final com.rspsi.editor.SessionChangeListener listener = this::changed;
    private RenderScene scene;
    private boolean closed;

    public SessionSceneController(EditorSession session, SceneRenderer renderer) {
        this(session, renderer, new RenderSceneBuilder());
    }

    public SessionSceneController(EditorSession session, SceneRenderer renderer,
                                  RenderSceneBuilder scenes) {
        this.session = Objects.requireNonNull(session, "session");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scenes = Objects.requireNonNull(scenes, "scenes");
        this.scene = scenes.build(session.world());
        renderer.load(scene);
        session.addChangeListener(listener);
    }

    public RenderScene scene() {
        return scene;
    }

    /**
     * Applies the currently queued session invalidations. This is public for
     * renderers that schedule updates on a separate frame loop; ordinary
     * command execution invokes it automatically through the listener.
     */
    public void refresh() {
        ensureOpen();
        Set<DirtyRegion> dirty = session.dirtyRegions();
        if (dirty.isEmpty()) return;
        RenderChanges changes = RenderChanges.fromDirtyRegions(dirty, session.world());
        RenderScene next = scenes.update(scene, changes);
        renderer.update(next, changes);
        scene = next;
        session.drainDirtyRegions();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        session.removeChangeListener(listener);
    }

    private void changed(Set<TileCoordinate> ignored) {
        if (!closed) refresh();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Scene controller is closed");
    }
}
