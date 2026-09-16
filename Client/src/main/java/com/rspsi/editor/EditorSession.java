package com.rspsi.editor;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.model.DirtyRegion;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns editor state without requiring JavaFX or a renderer. */
public final class EditorSession {
    private final WorldDocument world;
    private final SessionSaveHandler saveHandler;
    private final SelectionModel selection = new SelectionModel();
    private final CommandHistory history = new CommandHistory();
    private final List<SessionChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final List<SessionStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final Map<Long, DirtyRegion> dirtyRegions = new LinkedHashMap<>();
    private int savedHistoryPosition;

    public EditorSession(WorldDocument world) {
        this(world, null);
    }

    /** Creates a session with an optional neutral persistence callback. */
    public EditorSession(WorldDocument world, SessionSaveHandler saveHandler) {
        this.world = Objects.requireNonNull(world, "world");
        this.saveHandler = saveHandler;
    }

    public WorldDocument world() {
        return world;
    }

    /** @deprecated use {@link #world()}. */
    @Deprecated
    public WorldModel worldModel() {
        if (!(world instanceof WorldModel legacy)) {
            throw new IllegalStateException("Session document is not a legacy WorldModel");
        }
        return legacy;
    }

    public SelectionModel selection() {
        return selection;
    }

    public CommandHistory history() {
        return history;
    }

    public void execute(EditorCommand command) {
        EditorCommand checked = Objects.requireNonNull(command, "command");
        history.execute(checked, this);
        notifyChanged(checked);
        notifyStateChanged();
    }

    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        EditorCommand command = history.previousCommand();
        boolean changed = history.undo(this);
        if (changed) {
            notifyChanged(command);
        }
        if (changed) {
            notifyStateChanged();
        }
        return changed;
    }

    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        EditorCommand command = history.nextCommand();
        boolean changed = history.redo(this);
        if (changed) {
            notifyChanged(command);
        }
        if (changed) {
            notifyStateChanged();
        }
        return changed;
    }

    public void markSaved() {
        savedHistoryPosition = history.position();
        notifyStateChanged();
    }

    /** Returns whether this session has a persistence callback configured. */
    public boolean canSave() {
        return saveHandler != null;
    }

    /** Persists this session through its configured neutral save boundary. */
    public void save() {
        if (saveHandler == null) {
            throw new IllegalStateException("Session has no save handler");
        }
        saveHandler.save(this);
    }

    public boolean isDirty() {
        return history.position() != savedHistoryPosition;
    }

    /** Provides the saved-history marker to neutral status/diagnostic views. */
    public int savedHistoryPosition() {
        return savedHistoryPosition;
    }

    /** Returns a stable snapshot of chunks whose derived data needs rebuilding. */
    public synchronized Set<DirtyRegion> dirtyRegions() {
        return Set.copyOf(dirtyRegions.values());
    }

    /** Returns and clears the current invalidation batch for a renderer/cache writer. */
    public synchronized Set<DirtyRegion> drainDirtyRegions() {
        Set<DirtyRegion> result = Set.copyOf(dirtyRegions.values());
        dirtyRegions.clear();
        return result;
    }

    public void addChangeListener(SessionChangeListener listener) {
        changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(SessionChangeListener listener) {
        changeListeners.remove(listener);
    }

    public void addStateListener(SessionStateListener listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeStateListener(SessionStateListener listener) {
        stateListeners.remove(listener);
    }

    private void notifyChanged(EditorCommand command) {
        if (command.changedTiles().isEmpty()) {
            return;
        }
        markDirty(command.changedTiles());
        var changedTiles = Set.copyOf(command.changedTiles());
        for (SessionChangeListener listener : changeListeners) {
            listener.changed(changedTiles);
        }
    }

    private void notifyStateChanged() {
        for (SessionStateListener listener : stateListeners) {
            listener.changed(this);
        }
    }

    private synchronized void markDirty(Set<com.rspsi.editor.model.TileCoordinate> changedTiles) {
        for (var coordinate : changedTiles) {
            markDirtyChunk(coordinate);
            // A tile on a chunk edge can affect blended floors, shared-edge
            // geometry, and picking in the adjacent 8x8 chunk.
            if (coordinate.x() % 8 == 0 && coordinate.x() > 0) {
                markDirtyChunk(new com.rspsi.editor.model.TileCoordinate(
                        coordinate.plane(), coordinate.x() - 1, coordinate.y()));
            }
            if (coordinate.x() % 8 == 7 && coordinate.x() + 1 < world.width()) {
                markDirtyChunk(new com.rspsi.editor.model.TileCoordinate(
                        coordinate.plane(), coordinate.x() + 1, coordinate.y()));
            }
            if (coordinate.y() % 8 == 0 && coordinate.y() > 0) {
                markDirtyChunk(new com.rspsi.editor.model.TileCoordinate(
                        coordinate.plane(), coordinate.x(), coordinate.y() - 1));
            }
            if (coordinate.y() % 8 == 7 && coordinate.y() + 1 < world.length()) {
                markDirtyChunk(new com.rspsi.editor.model.TileCoordinate(
                        coordinate.plane(), coordinate.x(), coordinate.y() + 1));
            }
        }
    }

    private void markDirtyChunk(com.rspsi.editor.model.TileCoordinate coordinate) {
        DirtyRegion dirty = DirtyRegion.forTile(coordinate);
        long key = ((long) dirty.chunkX() << 32) | (dirty.chunkY() & 0xFFFFFFFFL);
        dirtyRegions.merge(key, dirty, DirtyRegion::merge);
    }
}
