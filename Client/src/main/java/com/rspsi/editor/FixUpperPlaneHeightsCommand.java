package com.rspsi.editor;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rebuilds upper-plane corner heights from plane zero as one undoable edit.
 *
 * <p>This is the neutral equivalent of the legacy “fix heights” action. The
 * command deliberately preserves every non-height field and calculates each
 * plane from the original plane-zero snapshot, so applying it does not depend
 * on command iteration order.</p>
 */
public final class FixUpperPlaneHeightsCommand implements EditorCommand {
    private static final int PLANE_HEIGHT_STEP = 240;

    private final String description;
    private List<SetTileCommand> delegates;

    public FixUpperPlaneHeightsCommand() {
        this("Fix upper-plane heights");
    }

    public FixUpperPlaneHeightsCommand(String description) {
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public void apply(EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (delegates == null) {
            delegates = build(session);
        }
        CommandTransaction.apply(delegates, session);
    }

    @Override
    public void undo(EditorSession session) {
        Objects.requireNonNull(session, "session");
        if (delegates == null) {
            throw new IllegalStateException("Height-fix command has not been applied");
        }
        for (int index = delegates.size() - 1; index >= 0; index--) {
            delegates.get(index).undo(session);
        }
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Set<TileCoordinate> changedTiles() {
        if (delegates == null) {
            return Set.of();
        }
        Set<TileCoordinate> changed = new LinkedHashSet<>();
        for (SetTileCommand delegate : delegates) {
            changed.addAll(delegate.changedTiles());
        }
        return Set.copyOf(changed);
    }

    private List<SetTileCommand> build(EditorSession session) {
        var world = session.world();
        List<SetTileCommand> commands = new ArrayList<>();
        for (int plane = 1; plane < world.planes(); plane++) {
            int offset = plane * PLANE_HEIGHT_STEP;
            for (int x = 0; x < world.width(); x++) {
                for (int y = 0; y < world.length(); y++) {
                    TileCoordinate coordinate = new TileCoordinate(plane, x, y);
                    TileSnapshot before = world.tile(coordinate).snapshot();
                    TileSnapshot base = world.tile(0, x, y).snapshot();
                    TileSnapshot after = new TileSnapshot(
                            base.southWestHeight() - offset,
                            base.southEastHeight() - offset,
                            base.northEastHeight() - offset,
                            base.northWestHeight() - offset,
                            before.underlayId(), before.overlayId(),
                            before.overlayShape(), before.overlayRotation(),
                            before.flags(), before.objects());
                    commands.add(new SetTileCommand(coordinate, before, after, description));
                }
            }
        }
        return List.copyOf(commands);
    }
}
