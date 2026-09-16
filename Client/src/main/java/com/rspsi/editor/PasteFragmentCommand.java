package com.rspsi.editor;

import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies a complete fragment as one undoable history entry. */
public final class PasteFragmentCommand implements EditorCommand {
    private final WorldFragment fragment;
    private final int targetX;
    private final int targetY;
    private final String description;
    private List<SetTileCommand> tileCommands;

    public PasteFragmentCommand(WorldFragment fragment, int targetX, int targetY) {
        this(fragment, targetX, targetY, "Paste world fragment");
    }

    public PasteFragmentCommand(WorldFragment fragment, int targetX, int targetY, String description) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.targetX = targetX;
        this.targetY = targetY;
        this.description = Objects.requireNonNull(description, "description");
        if (targetX < 0 || targetY < 0) {
            throw new IllegalArgumentException("Paste coordinates cannot be negative");
        }
    }

    @Override
    public void apply(EditorSession session) {
        if (tileCommands == null) {
            tileCommands = buildCommands(session);
        }
        for (SetTileCommand command : tileCommands) {
            command.apply(session);
        }
    }

    @Override
    public void undo(EditorSession session) {
        if (tileCommands == null) {
            throw new IllegalStateException("Fragment command has not been applied");
        }
        for (int index = tileCommands.size() - 1; index >= 0; index--) {
            tileCommands.get(index).undo(session);
        }
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Set<TileCoordinate> changedTiles() {
        return fragment.terrain().stream()
                .map(patch -> target(patch, fragment.bounds(), targetX, targetY))
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<SetTileCommand> buildCommands(EditorSession session) {
        TileBounds sourceBounds = fragment.bounds();
        int deltaX = targetX - sourceBounds.minX();
        int deltaY = targetY - sourceBounds.minY();
        List<SetTileCommand> commands = new ArrayList<>();
        for (TerrainTilePatch patch : fragment.terrain()) {
            TileCoordinate coordinate = new TileCoordinate(patch.plane(), patch.x() + deltaX, patch.y() + deltaY);
            TileSnapshot before = session.world().tile(coordinate).snapshot();
            List<WorldObject> objects = fragment.objects().stream()
                    .filter(object -> object.plane() == patch.plane()
                            && object.x() == patch.x() && object.y() == patch.y())
                    .map(object -> new WorldObject(object.id(), object.type(), object.rotation(),
                            object.plane(), object.x() + deltaX, object.y() + deltaY))
                    .toList();
            TileSnapshot source = patch.snapshot();
            TileSnapshot after = new TileSnapshot(source.southWestHeight(), source.southEastHeight(),
                    source.northEastHeight(), source.northWestHeight(), source.underlayId(),
                    source.overlayId(), source.overlayShape(), source.overlayRotation(),
                    source.flags(), objects);
            commands.add(new SetTileCommand(coordinate, before, after, description));
        }
        return List.copyOf(commands);
    }

    private static TileCoordinate target(TerrainTilePatch patch, TileBounds bounds, int targetX, int targetY) {
        return new TileCoordinate(patch.plane(), targetX + patch.x() - bounds.minX(),
                targetY + patch.y() - bounds.minY());
    }
}
