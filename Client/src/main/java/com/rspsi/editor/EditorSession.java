package com.rspsi.editor;

import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.model.DirtyRegion;
import com.rspsi.editor.model.DocumentCoordinates;
import com.rspsi.editor.model.WorldWindow;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Owns editor state without requiring JavaFX or a renderer. */
public final class EditorSession {
    private final WorldDocument world;
    private final WorldWindow window;
    private final DocumentCoordinates coordinates;
    private final SessionSaveHandler saveHandler;
    private final boolean editable;
    private final SelectionModel selection = new SelectionModel();
    private final CommandHistory history = new CommandHistory();
    private final List<SessionChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private final List<SessionStateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final Map<DirtyChunkKey, DirtyRegion> dirtyRegions = new LinkedHashMap<>();
    private List<EditorCommand> savedSessionCommands = List.of();
    private int savedHistoryPosition;

    public EditorSession(WorldDocument world) {
        this(world, new WorldWindow(0, 0, world.width(), world.length()), null, true);
    }

    public EditorSession(WorldDocument world, WorldWindow window) {
        this(world, window, null, true);
    }

    /** Creates a session with an optional neutral persistence callback. */
    public EditorSession(WorldDocument world, SessionSaveHandler saveHandler) {
        this(world, new WorldWindow(0, 0, world.width(), world.length()), saveHandler, true);
    }

    /** Creates a region/window-aware session with an optional persistence callback. */
    public EditorSession(WorldDocument world, WorldWindow window, SessionSaveHandler saveHandler) {
        this(world, window, saveHandler, true);
    }

    private EditorSession(WorldDocument world, WorldWindow window,
                          SessionSaveHandler saveHandler, boolean editable) {
        this.world = Objects.requireNonNull(world, "world");
        this.window = Objects.requireNonNull(window, "window");
        this.coordinates = new DocumentCoordinates(this.world, this.window);
        this.saveHandler = saveHandler;
        this.editable = editable;
    }

    /** Creates an inspect-only session that rejects all document mutations. */
    public static EditorSession readOnly(WorldDocument world) {
        return new EditorSession(world,
                new WorldWindow(0, 0, world.width(), world.length()), null, false);
    }

    /** Creates an inspect-only region/window-aware session. */
    public static EditorSession readOnly(WorldDocument world, WorldWindow window) {
        return new EditorSession(world, window, null, false);
    }

    public WorldDocument world() {
        return world;
    }

    /** Absolute placement of this document in the OSRS world. */
    public WorldWindow window() {
        return window;
    }

    /** Canonical world/local conversion service for this session. */
    public DocumentCoordinates coordinates() {
        return coordinates;
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
        if (!editable) {
            throw new UnsupportedOperationException("Editor session is read-only");
        }
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

    /** Moves to an exact history position and emits one consolidated update. */
    public boolean jumpToHistory(int position) {
        if (!editable) {
            throw new UnsupportedOperationException("Editor session is read-only");
        }
        int before = history.position();
        if (before == position) return false;
        java.util.Set<com.rspsi.editor.model.TileCoordinate> changed = history.moveTo(position, this);
        notifyChanged(changed);
        notifyStateChanged();
        return true;
    }

    public void markSaved() {
        savedSessionCommands = currentSessionSaveState();
        savedHistoryPosition = history.position();
        notifyStateChanged();
    }

    /** Returns whether this session has a persistence callback configured. */
    public boolean canSave() {
        return saveHandler != null;
    }

    /** Returns whether this session accepts editing commands. */
    public boolean canEdit() {
        return editable;
    }

    /** Persists this session through its configured neutral save boundary. */
    public void save() {
        if (saveHandler == null) {
            throw new IllegalStateException("Session has no save handler");
        }
        saveHandler.save(this);
    }

    /**
     * Returns whether the normal session save handler has durable changes to
     * write. External transactions are deliberately excluded from this check.
     */
    public boolean isSessionSaveDirty() {
        return !currentSessionSaveState().equals(savedSessionCommands);
    }

    /**
     * Returns whether an applied command owns dirty state outside the normal
     * session save handler, such as an in-memory definition transaction.
     */
    public boolean hasUnsavedExternalState() {
        return appliedCommands().stream()
                .filter(command -> !command.savedBySessionSave())
                .anyMatch(EditorCommand::hasUnsavedExternalState);
    }

    public boolean isDirty() {
        return isSessionSaveDirty() || hasUnsavedExternalState();
    }

    /** Provides the saved-history marker to neutral status/diagnostic views. */
    public int savedHistoryPosition() {
        return savedHistoryPosition;
    }

    private List<EditorCommand> currentSessionSaveState() {
        return appliedCommands().stream()
                .filter(EditorCommand::savedBySessionSave)
                .toList();
    }

    private List<EditorCommand> appliedCommands() {
        List<EditorCommand> commands = history.commands();
        return List.copyOf(commands.subList(0, history.position()));
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
        notifyChanged(command.changedTiles());
    }

    private void notifyChanged(Set<com.rspsi.editor.model.TileCoordinate> changedTiles) {
        if (changedTiles.isEmpty()) {
            return;
        }
        markDirty(changedTiles);
        Set<com.rspsi.editor.model.TileCoordinate> snapshot = Set.copyOf(changedTiles);
        for (SessionChangeListener listener : changeListeners) {
            listener.changed(snapshot);
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
        DirtyChunkKey key = new DirtyChunkKey(dirty.plane(), dirty.chunkX(), dirty.chunkY());
        dirtyRegions.merge(key, dirty, DirtyRegion::merge);
    }

    private record DirtyChunkKey(int plane, int chunkX, int chunkY) {
    }
}
