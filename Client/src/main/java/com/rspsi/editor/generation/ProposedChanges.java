package com.rspsi.editor.generation;

import com.rspsi.editor.EditorCommand;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.model.Tile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable container representing non-destructive changes proposed by a generator or algorithm.
 *
 * <p>A proposal can be previewed in the viewport before being converted into an undoable
 * {@link EditorCommand} for commit.</p>
 */
public record ProposedChanges(
        Map<TileCoordinate, TileSnapshot> proposedTiles,
        List<WorldObject> addedObjects,
        List<WorldObject> removedObjects,
        List<String> diagnostics
) {
    public ProposedChanges {
        Objects.requireNonNull(proposedTiles, "proposedTiles");
        Objects.requireNonNull(addedObjects, "addedObjects");
        Objects.requireNonNull(removedObjects, "removedObjects");
        Objects.requireNonNull(diagnostics, "diagnostics");
        proposedTiles = Collections.unmodifiableMap(Map.copyOf(proposedTiles));
        addedObjects = Collections.unmodifiableList(List.copyOf(addedObjects));
        removedObjects = Collections.unmodifiableList(List.copyOf(removedObjects));
        diagnostics = Collections.unmodifiableList(List.copyOf(diagnostics));
    }

    /** Returns whether this proposal contains any tile or object modifications. */
    public boolean isEmpty() {
        return proposedTiles.isEmpty() && addedObjects.isEmpty() && removedObjects.isEmpty();
    }

    /**
     * Returns the set of all tile coordinates that will be affected if these changes are applied.
     */
    public Set<TileCoordinate> affectedCoordinates() {
        Set<TileCoordinate> coordinates = new HashSet<>(proposedTiles.keySet());
        for (WorldObject obj : addedObjects) {
            coordinates.add(new TileCoordinate(obj.plane(), obj.x(), obj.y()));
        }
        for (WorldObject obj : removedObjects) {
            coordinates.add(new TileCoordinate(obj.plane(), obj.x(), obj.y()));
        }
        return Collections.unmodifiableSet(coordinates);
    }

    /**
     * Converts this proposal into a fully undoable and redoable {@link EditorCommand}.
     */
    public EditorCommand toCommand(String description) {
        return new ProposedChangesCommand(this, description);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<TileCoordinate, TileSnapshot> proposedTiles = new HashMap<>();
        private final List<WorldObject> addedObjects = new ArrayList<>();
        private final List<WorldObject> removedObjects = new ArrayList<>();
        private final List<String> diagnostics = new ArrayList<>();

        public Builder setTile(TileCoordinate coordinate, TileSnapshot snapshot) {
            proposedTiles.put(Objects.requireNonNull(coordinate, "coordinate"),
                    Objects.requireNonNull(snapshot, "snapshot"));
            return this;
        }

        public Builder addObject(WorldObject object) {
            addedObjects.add(Objects.requireNonNull(object, "object"));
            return this;
        }

        public Builder removeObject(WorldObject object) {
            removedObjects.add(Objects.requireNonNull(object, "object"));
            return this;
        }

        public Builder addDiagnostic(String diagnostic) {
            diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            return this;
        }

        public ProposedChanges build() {
            return new ProposedChanges(proposedTiles, addedObjects, removedObjects, diagnostics);
        }
    }

    private static final class ProposedChangesCommand implements EditorCommand {
        private final ProposedChanges changes;
        private final String description;
        private final Set<TileCoordinate> changedTiles;
        private Map<TileCoordinate, TileSnapshot> rollbackSnapshots;

        ProposedChangesCommand(ProposedChanges changes, String description) {
            this.changes = Objects.requireNonNull(changes, "changes");
            this.description = Objects.requireNonNull(description, "description");
            this.changedTiles = changes.affectedCoordinates();
        }

        @Override
        public void apply(EditorSession session) {
            WorldDocument world = session.world();
            if (rollbackSnapshots == null) {
                rollbackSnapshots = new HashMap<>();
                for (TileCoordinate coord : changedTiles) {
                    if (isValidCoordinate(world, coord)) {
                        rollbackSnapshots.put(coord, world.tile(coord).snapshot());
                    }
                }
            }

            // Apply direct tile snapshots
            for (var entry : changes.proposedTiles().entrySet()) {
                TileCoordinate coord = entry.getKey();
                if (isValidCoordinate(world, coord)) {
                    world.tile(coord).restore(entry.getValue());
                }
            }

            // Apply added objects if not already present
            for (WorldObject obj : changes.addedObjects()) {
                TileCoordinate coord = new TileCoordinate(obj.plane(), obj.x(), obj.y());
                if (isValidCoordinate(world, coord)) {
                    Tile tile = world.tile(coord);
                    TileSnapshot current = tile.snapshot();
                    if (!current.objects().contains(obj)) {
                        List<WorldObject> updatedObjects = new ArrayList<>(current.objects());
                        updatedObjects.add(obj);
                        tile.restore(new TileSnapshot(
                                current.southWestHeight(), current.southEastHeight(),
                                current.northEastHeight(), current.northWestHeight(),
                                current.underlayId(), current.overlayId(),
                                current.overlayShape(), current.overlayRotation(),
                                current.flags(), updatedObjects
                        ));
                    }
                }
            }

            // Apply removed objects
            for (WorldObject obj : changes.removedObjects()) {
                TileCoordinate coord = new TileCoordinate(obj.plane(), obj.x(), obj.y());
                if (isValidCoordinate(world, coord)) {
                    Tile tile = world.tile(coord);
                    TileSnapshot current = tile.snapshot();
                    if (current.objects().contains(obj)) {
                        List<WorldObject> updatedObjects = new ArrayList<>(current.objects());
                        updatedObjects.remove(obj);
                        tile.restore(new TileSnapshot(
                                current.southWestHeight(), current.southEastHeight(),
                                current.northEastHeight(), current.northWestHeight(),
                                current.underlayId(), current.overlayId(),
                                current.overlayShape(), current.overlayRotation(),
                                current.flags(), updatedObjects
                        ));
                    }
                }
            }
        }

        @Override
        public void undo(EditorSession session) {
            if (rollbackSnapshots == null) return;
            WorldDocument world = session.world();
            for (var entry : rollbackSnapshots.entrySet()) {
                TileCoordinate coord = entry.getKey();
                if (isValidCoordinate(world, coord)) {
                    world.tile(coord).restore(entry.getValue());
                }
            }
        }

        @Override
        public Set<TileCoordinate> changedTiles() {
            return changedTiles;
        }

        @Override
        public String description() {
            return description;
        }

        private static boolean isValidCoordinate(WorldDocument world, TileCoordinate coord) {
            return coord.plane() >= 0 && coord.plane() < world.planes()
                    && coord.x() >= 0 && coord.x() < world.width()
                    && coord.y() >= 0 && coord.y() < world.length();
        }
    }
}
