package com.rspsi.editor;

import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;

import java.util.Objects;
import java.util.Set;

/** Explicit undoable terrain-height mutation with provenance-aware apply/undo. */
public final class SetTerrainHeightCommand implements EditorCommand {
    private final TileCoordinate coordinate;
    private final TileSnapshot before;
    private final TileSnapshot after;
    private final TerrainHeightSource beforeSource;
    private final TerrainHeightSource afterSource;
    private final String description;

    public SetTerrainHeightCommand(TileCoordinate coordinate, TileSnapshot before,
                                   TileSnapshot after, String description) {
        this(coordinate, before, after, before.heightSource(),
                TerrainHeightSource.authoredSource(), description);
    }

    public SetTerrainHeightCommand(TileCoordinate coordinate, TileSnapshot before,
                                   TileSnapshot after, TerrainHeightSource beforeSource,
                                   TerrainHeightSource afterSource, String description) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.beforeSource = Objects.requireNonNull(beforeSource, "beforeSource");
        this.afterSource = Objects.requireNonNull(afterSource, "afterSource");
        this.description = Objects.requireNonNull(description, "description");
        if (!sameNonHeightState(before, after)) {
            throw new IllegalArgumentException("Terrain height command cannot change non-height tile state");
        }
    }

    @Override public void apply(EditorSession session) {
        session.world().tile(coordinate).restore(after, afterSource);
    }

    @Override public void undo(EditorSession session) {
        session.world().tile(coordinate).restore(before, beforeSource);
    }

    @Override public String description() { return description; }
    @Override public Set<TileCoordinate> changedTiles() { return Set.of(coordinate); }

    private static boolean sameNonHeightState(TileSnapshot a, TileSnapshot b) {
        return a.underlayId() == b.underlayId()
                && a.overlayId() == b.overlayId()
                && a.overlayShape() == b.overlayShape()
                && a.overlayRotation() == b.overlayRotation()
                && a.flags() == b.flags()
                && a.objects().equals(b.objects());
    }
}
