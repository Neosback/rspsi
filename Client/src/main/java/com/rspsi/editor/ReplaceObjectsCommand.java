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

/** Replaces the definition ID of selected objects as one atomic edit. */
public final class ReplaceObjectsCommand implements EditorCommand {
    private final Set<WorldObject> objects;
    private final int replacementId;
    private final String description;
    private List<SetTileCommand> delegates;

    public ReplaceObjectsCommand(Set<WorldObject> objects, int replacementId) {
        this(objects, replacementId, "Replace selected objects");
    }

    public ReplaceObjectsCommand(Set<WorldObject> objects, int replacementId, String description) {
        this.objects = Set.copyOf(Objects.requireNonNull(objects, "objects"));
        if (this.objects.isEmpty()) throw new IllegalArgumentException("Object selection cannot be empty");
        if (replacementId < 0) throw new IllegalArgumentException("Replacement object ID cannot be negative");
        this.replacementId = replacementId;
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
        return objects.stream().map(object -> new TileCoordinate(object.plane(), object.x(), object.y()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<WorldObject> replacementObjects() {
        return objects.stream().map(this::replacement).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private List<SetTileCommand> create(EditorSession session) {
        Map<TileCoordinate, TileSnapshot> before = new LinkedHashMap<>();
        Map<TileCoordinate, List<WorldObject>> after = new LinkedHashMap<>();
        for (WorldObject object : objects) {
            TileCoordinate coordinate = coordinate(session, object);
            before.putIfAbsent(coordinate, session.world().tile(coordinate).snapshot());
        }
        before.forEach((coordinate, snapshot) -> after.put(coordinate, new ArrayList<>(snapshot.objects())));
        for (WorldObject object : objects) {
            TileCoordinate coordinate = coordinate(session, object);
            List<WorldObject> tileObjects = after.get(coordinate);
            if (!tileObjects.remove(object)) {
                throw new IllegalArgumentException("Selected object is not present at its owning tile: " + object);
            }
            tileObjects.add(replacement(object));
        }
        List<SetTileCommand> commands = new ArrayList<>();
        for (Map.Entry<TileCoordinate, TileSnapshot> entry : before.entrySet()) {
            commands.add(new SetTileCommand(entry.getKey(), entry.getValue(),
                    PlaceObjectCommand.copyWithObjects(entry.getValue(), after.get(entry.getKey())), description));
        }
        return List.copyOf(commands);
    }

    private static TileCoordinate coordinate(EditorSession session, WorldObject object) {
        if (object.plane() >= session.world().planes() || object.x() >= session.world().width()
                || object.y() >= session.world().length()) {
            throw new IllegalArgumentException("Selected object is outside the current world");
        }
        return new TileCoordinate(object.plane(), object.x(), object.y());
    }

    private WorldObject replacement(WorldObject object) {
        return new WorldObject(replacementId, object.type(), object.rotation(), object.plane(), object.x(), object.y());
    }
}
