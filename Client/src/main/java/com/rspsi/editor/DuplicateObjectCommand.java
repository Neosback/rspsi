package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldObject;

import java.util.Objects;
import java.util.Set;

/** Places a copy of one object at a second tile as one undoable edit. */
public final class DuplicateObjectCommand implements EditorCommand {
    private final WorldObject source;
    private final int targetX;
    private final int targetY;
    private final String description;
    private PlaceObjectCommand delegate;

    public DuplicateObjectCommand(WorldObject source, int targetX, int targetY) {
        this(source, targetX, targetY, "Duplicate object");
    }

    public DuplicateObjectCommand(WorldObject source, int targetX, int targetY, String description) {
        this.source = Objects.requireNonNull(source, "source");
        if (targetX < 0 || targetY < 0) throw new IllegalArgumentException("Object coordinates cannot be negative");
        this.targetX = targetX;
        this.targetY = targetY;
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override public void apply(EditorSession session) {
        if (delegate == null) {
            delegate = new PlaceObjectCommand(new WorldObject(source.id(), source.type(), source.rotation(),
                    source.plane(), targetX, targetY), description);
        }
        delegate.apply(session);
    }
    @Override public void undo(EditorSession session) { requireDelegate().undo(session); }
    @Override public String description() { return description; }
    @Override public Set<TileCoordinate> changedTiles() {
        return Set.of(new TileCoordinate(source.plane(), targetX, targetY));
    }
    private PlaceObjectCommand requireDelegate() {
        if (delegate == null) throw new IllegalStateException("Object command has not been applied");
        return delegate;
    }
}
