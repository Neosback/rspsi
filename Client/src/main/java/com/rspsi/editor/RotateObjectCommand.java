package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Changes one object's orientation without changing its location. */
public final class RotateObjectCommand implements EditorCommand {
    private final WorldObject object;
    private final int rotation;
    private final String description;
    private SetTileCommand delegate;

    public RotateObjectCommand(WorldObject object, int rotation) {
        this(object, rotation, "Rotate object");
    }
    public RotateObjectCommand(WorldObject object, int rotation, String description) {
        this.object = Objects.requireNonNull(object, "object");
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Object rotation must be between 0 and 3");
        this.rotation = rotation;
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
        int found = objects.indexOf(object);
        if (found >= 0) objects.set(found, new WorldObject(object.id(), object.type(), rotation,
                object.plane(), object.x(), object.y()));
        return new SetTileCommand(coordinate, before, PlaceObjectCommand.copyWithObjects(before, objects), description);
    }
    private TileCoordinate coordinate() { return new TileCoordinate(object.plane(), object.x(), object.y()); }
    private SetTileCommand requireDelegate() {
        if (delegate == null) throw new IllegalStateException("Object command has not been applied");
        return delegate;
    }
}
