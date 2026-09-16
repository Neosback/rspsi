package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deletes one canonical world object from its owning tile. */
public final class DeleteObjectCommand implements EditorCommand {
    private final WorldObject object;
    private final String description;
    private SetTileCommand delegate;

    public DeleteObjectCommand(WorldObject object) { this(object, "Delete object"); }
    public DeleteObjectCommand(WorldObject object, String description) {
        this.object = Objects.requireNonNull(object, "object");
        this.description = Objects.requireNonNull(description, "description");
    }
    @Override public void apply(EditorSession session) {
        if (delegate == null) delegate = create(session);
        delegate.apply(session);
    }
    @Override public void undo(EditorSession session) { requireDelegate().undo(session); }
    @Override public String description() { return description; }
    @Override public Set<TileCoordinate> changedTiles() { return Set.of(coordinate()); }
    private SetTileCommand create(EditorSession session) {
        TileCoordinate coordinate = coordinate();
        TileSnapshot before = session.world().tile(coordinate).snapshot();
        List<WorldObject> objects = new ArrayList<>(before.objects());
        objects.remove(object);
        return new SetTileCommand(coordinate, before, PlaceObjectCommand.copyWithObjects(before, objects), description);
    }
    private TileCoordinate coordinate() { return new TileCoordinate(object.plane(), object.x(), object.y()); }
    private SetTileCommand requireDelegate() {
        if (delegate == null) throw new IllegalStateException("Object command has not been applied");
        return delegate;
    }
}
