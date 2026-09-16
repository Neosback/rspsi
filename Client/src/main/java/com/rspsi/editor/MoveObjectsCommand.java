package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Moves a selected set of objects as one atomic history entry. */
public final class MoveObjectsCommand implements EditorCommand {
    private final Set<WorldObject> objects;
    private final int deltaX;
    private final int deltaY;
    private final String description;
    private List<SetTileCommand> delegates;

    public MoveObjectsCommand(Set<WorldObject> objects, int deltaX, int deltaY) {
        this(objects, deltaX, deltaY, "Move selected objects");
    }

    public MoveObjectsCommand(Set<WorldObject> objects, int deltaX, int deltaY, String description) {
        this.objects = Set.copyOf(Objects.requireNonNull(objects, "objects"));
        if (this.objects.isEmpty()) throw new IllegalArgumentException("Object selection cannot be empty");
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override public void apply(EditorSession session) {
        if (delegates == null) delegates = create(session);
        delegates.forEach(command -> command.apply(session));
    }

    @Override public void undo(EditorSession session) {
        if (delegates == null) throw new IllegalStateException("Object command has not been applied");
        for (int index = delegates.size() - 1; index >= 0; index--) delegates.get(index).undo(session);
    }

    @Override public String description() { return description; }

    @Override public Set<TileCoordinate> changedTiles() {
        if (deltaX == 0 && deltaY == 0) return Set.of();
        Set<TileCoordinate> changed = new LinkedHashSet<>();
        for (WorldObject object : objects) {
            changed.add(new TileCoordinate(object.plane(), object.x(), object.y()));
            changed.add(new TileCoordinate(object.plane(), object.x() + deltaX, object.y() + deltaY));
        }
        return Set.copyOf(changed);
    }

    private List<SetTileCommand> create(EditorSession session) {
        Objects.requireNonNull(session, "session");
        Map<TileCoordinate, TileSnapshot> before = new LinkedHashMap<>();
        Map<TileCoordinate, List<WorldObject>> after = new LinkedHashMap<>();
        for (WorldObject object : objects) {
            TileCoordinate source = coordinate(object.x(), object.y(), object.plane());
            TileCoordinate destination = coordinate((long) object.x() + deltaX,
                    (long) object.y() + deltaY, object.plane());
            snapshot(session, before, source);
            snapshot(session, before, destination);
        }
        before.forEach((coordinate, snapshot) -> after.put(coordinate, new ArrayList<>(snapshot.objects())));

        for (WorldObject object : objects) {
            TileCoordinate source = coordinate(object.x(), object.y(), object.plane());
            TileCoordinate destination = coordinate((long) object.x() + deltaX,
                    (long) object.y() + deltaY, object.plane());
            if (!after.get(source).remove(object)) {
                throw new IllegalArgumentException("Selected object is not present at its owning tile: " + object);
            }
            WorldObject moved = new WorldObject(object.id(), object.type(), object.rotation(), object.plane(),
                    destination.x(), destination.y());
            if (!after.get(destination).contains(moved)) after.get(destination).add(moved);
        }

        List<SetTileCommand> commands = new ArrayList<>();
        for (Map.Entry<TileCoordinate, TileSnapshot> entry : before.entrySet()) {
            TileCoordinate coordinate = entry.getKey();
            commands.add(new SetTileCommand(coordinate, entry.getValue(),
                    PlaceObjectCommand.copyWithObjects(entry.getValue(), after.get(coordinate)), description));
        }
        return List.copyOf(commands);
    }

    private static void snapshot(EditorSession session, Map<TileCoordinate, TileSnapshot> before,
                                 TileCoordinate coordinate) {
        if (!before.containsKey(coordinate)) before.put(coordinate,
                session.world().tile(coordinate).snapshot());
    }

    private static TileCoordinate coordinate(long x, long y, int plane) {
        if (x < 0 || x > Integer.MAX_VALUE || y < 0 || y > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Object move leaves the valid coordinate range");
        }
        return new TileCoordinate(plane, (int) x, (int) y);
    }
}
