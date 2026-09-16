package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Moves one canonical object between owning tiles as one atomic command. */
public final class MoveObjectCommand implements EditorCommand {
    private final WorldObject object;
    private final int targetX;
    private final int targetY;
    private final String description;
    private List<SetTileCommand> delegates;

    public MoveObjectCommand(WorldObject object, int targetX, int targetY) {
        this(object, targetX, targetY, "Move object");
    }
    public MoveObjectCommand(WorldObject object, int targetX, int targetY, String description) {
        this.object = Objects.requireNonNull(object, "object");
        if (targetX < 0 || targetY < 0) throw new IllegalArgumentException("Object coordinates cannot be negative");
        this.targetX = targetX;
        this.targetY = targetY;
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
        if (object.x() == targetX && object.y() == targetY) return Set.of();
        return Set.of(new TileCoordinate(object.plane(), object.x(), object.y()),
                new TileCoordinate(object.plane(), targetX, targetY));
    }
    private List<SetTileCommand> create(EditorSession session) {
        TileCoordinate sourceCoordinate = new TileCoordinate(object.plane(), object.x(), object.y());
        TileCoordinate targetCoordinate = new TileCoordinate(object.plane(), targetX, targetY);
        if (sourceCoordinate.equals(targetCoordinate)) return List.of();
        TileSnapshot sourceBefore = session.world().tile(sourceCoordinate).snapshot();
        TileSnapshot targetBefore = session.world().tile(targetCoordinate).snapshot();
        List<WorldObject> sourceObjects = new ArrayList<>(sourceBefore.objects());
        sourceObjects.remove(object);
        WorldObject moved = new WorldObject(object.id(), object.type(), object.rotation(), object.plane(), targetX, targetY);
        List<WorldObject> targetObjects = new ArrayList<>(targetBefore.objects());
        if (!targetObjects.contains(moved)) targetObjects.add(moved);
        return List.of(
                new SetTileCommand(sourceCoordinate, sourceBefore,
                        PlaceObjectCommand.copyWithObjects(sourceBefore, sourceObjects), description),
                new SetTileCommand(targetCoordinate, targetBefore,
                        PlaceObjectCommand.copyWithObjects(targetBefore, targetObjects), description));
    }
}
