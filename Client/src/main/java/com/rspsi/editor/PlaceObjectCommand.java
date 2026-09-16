package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Places one canonical world object at its owning tile. */
public final class PlaceObjectCommand implements EditorCommand {
    private final WorldObject object;
    private final String description;
    private SetTileCommand delegate;

    public PlaceObjectCommand(WorldObject object) {
        this(object, "Place object");
    }

    public PlaceObjectCommand(WorldObject object, String description) {
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
        if (!objects.contains(object)) objects.add(object);
        TileSnapshot after = copyWithObjects(before, objects);
        return new SetTileCommand(coordinate, before, after, description);
    }
    private TileCoordinate coordinate() { return new TileCoordinate(object.plane(), object.x(), object.y()); }
    private SetTileCommand requireDelegate() {
        if (delegate == null) throw new IllegalStateException("Object command has not been applied");
        return delegate;
    }
    static TileSnapshot copyWithObjects(TileSnapshot source, List<WorldObject> objects) {
        return new TileSnapshot(source.southWestHeight(), source.southEastHeight(), source.northEastHeight(),
                source.northWestHeight(), source.underlayId(), source.overlayId(), source.overlayShape(),
                source.overlayRotation(), source.flags(), objects);
    }
}
