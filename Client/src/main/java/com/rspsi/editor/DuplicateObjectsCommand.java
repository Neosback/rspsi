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

/** Duplicates a selected set of objects as one atomic history entry. */
public final class DuplicateObjectsCommand implements EditorCommand {
    private final Set<WorldObject> objects;
    private final int deltaX;
    private final int deltaY;
    private final String description;
    private List<SetTileCommand> delegates;

    public DuplicateObjectsCommand(Set<WorldObject> objects, int deltaX, int deltaY) {
        this(objects, deltaX, deltaY, "Duplicate selected objects");
    }

    public DuplicateObjectsCommand(Set<WorldObject> objects, int deltaX, int deltaY, String description) {
        this.objects = Set.copyOf(Objects.requireNonNull(objects, "objects"));
        if (this.objects.isEmpty()) throw new IllegalArgumentException("Object selection cannot be empty");
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override public void apply(EditorSession session) {
        if (delegates == null) delegates = create(session);
        CommandTransaction.apply(delegates, session);
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
            changed.add(coordinate((long) object.x() + deltaX, (long) object.y() + deltaY, object.plane()));
        }
        return Set.copyOf(changed);
    }

    private List<SetTileCommand> create(EditorSession session) {
        Objects.requireNonNull(session, "session");
        Map<TileCoordinate, TileSnapshot> before = new LinkedHashMap<>();
        Map<TileCoordinate, List<WorldObject>> after = new LinkedHashMap<>();
        for (WorldObject object : objects) {
            coordinate(object.x(), object.y(), object.plane());
            TileCoordinate destination = coordinate((long) object.x() + deltaX,
                    (long) object.y() + deltaY, object.plane());
            snapshot(session, before, destination);
        }
        before.forEach((coordinate, snapshot) -> after.put(coordinate, new ArrayList<>(snapshot.objects())));
        for (WorldObject object : objects) {
            TileCoordinate destination = coordinate((long) object.x() + deltaX,
                    (long) object.y() + deltaY, object.plane());
            WorldObject duplicate = new WorldObject(object.id(), object.type(), object.rotation(), object.plane(),
                    destination.x(), destination.y());
            List<WorldObject> destinationObjects = after.get(destination);
            if (!destinationObjects.contains(duplicate)) destinationObjects.add(duplicate);
        }

        List<SetTileCommand> commands = new ArrayList<>();
        for (Map.Entry<TileCoordinate, TileSnapshot> entry : before.entrySet()) {
            commands.add(new SetTileCommand(entry.getKey(), entry.getValue(),
                    PlaceObjectCommand.copyWithObjects(entry.getValue(), after.get(entry.getKey())), description));
        }
        return List.copyOf(commands);
    }

    private static void snapshot(EditorSession session, Map<TileCoordinate, TileSnapshot> before,
                                 TileCoordinate coordinate) {
        if (!before.containsKey(coordinate)) before.put(coordinate, session.world().tile(coordinate).snapshot());
    }

    private static TileCoordinate coordinate(long x, long y, int plane) {
        if (x < 0 || y < 0 || x > Integer.MAX_VALUE || y > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Object duplication leaves the valid coordinate range");
        }
        return new TileCoordinate(plane, (int) x, (int) y);
    }
}
