package com.rspsi.editor.change;

import com.rspsi.editor.model.TileBounds;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
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
        Provenance provenance,
        List<String> diagnostics
) {
    public ChangePlan {
        description = Objects.requireNonNull(description, "description");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Change plan description cannot be blank");
        }
        Objects.requireNonNull(tileChanges, "tileChanges");
        provenance = Objects.requireNonNull(provenance, "provenance");
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

    /** Inclusive XY bounds in absolute OSRS world-tile coordinates. */
    public Optional<TileBounds> affectedWorldBounds() {
        if (tileChanges.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (WorldTile tile : tileChanges.keySet()) {
            minX = Math.min(minX, tile.x());
            minY = Math.min(minY, tile.y());
            maxX = Math.max(maxX, tile.x());
            maxY = Math.max(maxY, tile.y());
        }
        return Optional.of(new TileBounds(minX, minY, maxX, maxY));
    }

    public Set<Integer> affectedPlanes() {
        Set<Integer> planes = new TreeSet<>();
        for (WorldTile tile : tileChanges.keySet()) {
            planes.add(tile.plane());
        }
        return Set.copyOf(planes);
    }

    public static Builder builder(String description) {
        return new Builder(description);
    }

    /**
     * Reproducibility metadata for generated plans. Manual edits use
     * {@link #manual()}; deterministic generators should provide the producer
     * ID and seed used to calculate the plan.
     */
    public record Provenance(
            String producer,
            OptionalLong seed,
            Map<String, String> attributes
    ) {
        public Provenance {
            producer = Objects.requireNonNull(producer, "producer");
            if (producer.isBlank()) {
                throw new IllegalArgumentException("Provenance producer cannot be blank");
            }
            seed = Objects.requireNonNull(seed, "seed");
            attributes = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(attributes, "attributes")));
        }

        public static Provenance manual() {
            return new Provenance("manual", OptionalLong.empty(), Map.of());
        }

        public static Provenance deterministic(String producer, long seed) {
            return new Provenance(producer, OptionalLong.of(seed), Map.of());
        }
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
        private Provenance provenance = Provenance.manual();
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

        public Builder provenance(Provenance provenance) {
            this.provenance = Objects.requireNonNull(provenance, "provenance");
            return this;
        }

        public Builder addDiagnostic(String diagnostic) {
            diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
            return this;
        }

        public ChangePlan build() {
            return new ChangePlan(description, changes, provenance, diagnostics);
        }
    }
}
