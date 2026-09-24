package com.rspsi.editor.change;

import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, world-space authoring proposal.
 *
 * <p>A change plan contains complete before/after tile states so it can be
 * previewed and validated without mutating a region. Object edits are covered
 * by the tile snapshot's object list; specialized object helpers can layer on
 * top without creating a second transaction format.</p>
 */
public record ChangePlan(
        String description,
        Map<WorldTile, TileChange> tileChanges,
        List<String> diagnostics
) {
    public ChangePlan {
        description = Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Change plan description cannot be blank");
        }
        Objects.requireNonNull(tileChanges, "tileChanges");
        Objects.requireNonNull(diagnostics, "diagnostics");
        tileChanges = Collections.unmodifiableMap(new LinkedHashMap<>(tileChanges));
        diagnostics = List.copyOf(diagnostics);
        for (var entry : tileChanges.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().tile())) {
                throw new IllegalArgumentException("Change-plan map key must equal tile change coordinate");
            }
        }
    }

    public boolean isEmpty() {
        return tileChanges.isEmpty();
    }

    public Set<WorldTile> affectedTiles() {
        return tileChanges.keySet();
    }

    public Set<Integer> affectedRegionIds() {
        Set<Integer> regions = new TreeSet<>();
        for (WorldTile tile : tileChanges.keySet()) {
            regions.add(tile.address().regionId());
        }
        return Set.copyOf(regions);
    }

    public static Builder builder(String description) {
        return new Builder(description);
    }

    public record TileChange(
            WorldTile tile,
            TileSnapshot before,
            TileSnapshot after
    ) {
        public TileChange {
            tile = Objects.requireNonNull(tile, "tile");
            before = Objects.requireNonNull(before, "before");
            after = Objects.requireNonNull(after, "after");
        }

        public boolean changesState() {
            return !before.equals(after);
        }
    }

    public static final class Builder {
        private final String description;
        private final Map<WorldTile, TileChange> changes = new LinkedHashMap<>();
        private final List<String> diagnostics = new ArrayList<>();

        private Builder(String description) {
            this.description = Objects.requireNonNull(description, "description");
            if (description.isBlank()) {
                throw new IllegalArgumentException("Change plan description cannot be blank");
            }
        }

        /**
         * Adds or replaces one proposed tile state. Exact no-op changes are
         * dropped so downstream partitioning and dirty tracking stay minimal.
         */
        public Builder setTile(WorldTile tile, TileSnapshot before, TileSnapshot after) {
            TileChange change = new TileChange(tile, before, after);
            if (change.changesState()) {
                changes.put(tile, change);
            } else {
                changes.remove(tile);
            }
            return this;
        }

        public Builder addDiagnostic(String diagnostic) {
            diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            return this;
        }

        public ChangePlan build() {
            return new ChangePlan(description, changes, diagnostics);
        }
    }
}
