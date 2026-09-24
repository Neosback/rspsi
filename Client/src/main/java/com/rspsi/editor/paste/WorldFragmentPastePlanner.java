package com.rspsi.editor.paste;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TerrainHeightSource;
import com.rspsi.editor.model.TerrainTilePatch;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldFragment;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldTileAddress;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Calculates non-destructive cross-region paste proposals for portable
 * {@link WorldFragment}s.
 *
 * <p>The planner never mutates canonical documents. Destination snapshots are
 * captured into a {@link ChangePlan}; {@link WorldRegionSessionWindow} remains
 * the only multi-region commit boundary.</p>
 */
public final class WorldFragmentPastePlanner {
    public static final String PRODUCER_ID = "studio.fragment.paste";

    private WorldFragmentPastePlanner() {
    }

    public static WorldFragmentPasteResult plan(
            WorldRegionSessionWindow window,
            WorldFragment fragment,
            int targetX,
            int targetY,
            WorldFragmentPastePolicy policy
    ) {
        return plan(window, fragment, targetX, targetY, policy, "Paste world fragment");
    }

    public static WorldFragmentPasteResult plan(
            WorldRegionSessionWindow window,
            WorldFragment fragment,
            int targetX,
            int targetY,
            WorldFragmentPastePolicy policy,
            String description
    ) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(policy, "policy");
        policy.validateFor(fragment);
        if (targetX < 0 || targetY < 0) {
            throw new IllegalArgumentException("Paste target coordinates cannot be negative");
        }
        String label = Objects.requireNonNull(description, "description").trim();
        if (label.isEmpty()) {
            throw new IllegalArgumentException("Paste description cannot be blank");
        }

        List<WorldFragmentPasteResult.Conflict> conflicts = new ArrayList<>();
        TreeMap<SourceTile, TerrainTilePatch> terrain = collectTerrain(
                fragment, targetX, targetY, conflicts);
        TreeMap<SourceTile, List<WorldObject>> objects = collectObjects(fragment);

        Integer heightDelta = resolveHeightDelta(
                window, fragment, targetX, targetY, policy, terrain, conflicts);

        List<SourceTile> scope = sourceScope(policy, terrain.keySet(), objects.keySet());
        ChangePlan.Builder plan = ChangePlan.builder(label)
                .provenance(new ChangePlan.Provenance(
                        PRODUCER_ID,
                        OptionalLong.empty(),
                        Map.of(
                                "terrain", policy.terrainMode().name(),
                                "objects", policy.objectMode().name(),
                                "heights", policy.heightMode().name(),
                                "conflicts", policy.conflictMode().name())));

        int skipped = 0;
        for (SourceTile source : scope) {
            WorldTile destination = translate(fragment, source, targetX, targetY);
            ResolvedDestination resolved = resolveEditable(window, destination, conflicts);
            if (resolved == null) {
                skipped++;
                continue;
            }

            TileSnapshot before = resolved.session().world()
                    .tile(resolved.local()).snapshot();
            TerrainTilePatch sourceTerrain = terrain.get(source);
            List<WorldObject> sourceObjects = objects.getOrDefault(source, List.of());

            TileSnapshot after = composeAfter(
                    before,
                    sourceTerrain,
                    sourceObjects,
                    resolved.local(),
                    policy,
                    heightDelta);

            plan.setTile(destination, before, after);
        }

        if (!conflicts.isEmpty()) {
            if (policy.conflictMode() == WorldFragmentPastePolicy.ConflictMode.SKIP) {
                plan.addDiagnostic("Skipped " + skipped
                        + " destination tile(s) with paste conflicts");
            } else {
                plan.addDiagnostic("Paste has " + conflicts.size()
                        + " unresolved planning conflict(s)");
            }
        }

        return new WorldFragmentPasteResult(
                plan.build(),
                conflicts,
                policy.conflictMode());
    }

    private static TreeMap<SourceTile, TerrainTilePatch> collectTerrain(
            WorldFragment fragment,
            int targetX,
            int targetY,
            List<WorldFragmentPasteResult.Conflict> conflicts
    ) {
        TreeMap<SourceTile, TerrainTilePatch> terrain = new TreeMap<>();
        for (TerrainTilePatch patch : fragment.terrain()) {
            if (!patch.snapshot().objects().isEmpty()) {
                throw new IllegalArgumentException(
                        "WorldFragment terrain snapshots must not embed objects; "
                                + "use fragment.objects()");
            }

            SourceTile source = new SourceTile(patch.plane(), patch.x(), patch.y());
            TerrainTilePatch existing = terrain.putIfAbsent(source, patch);
            if (existing != null) {
                conflicts.add(new WorldFragmentPasteResult.Conflict(
                        WorldFragmentPasteResult.ConflictCode.DUPLICATE_SOURCE_TILE,
                        translate(fragment, source, targetX, targetY),
                        "Fragment contains more than one terrain patch for the same source tile"));
            }
        }
        return terrain;
    }

    private static TreeMap<SourceTile, List<WorldObject>> collectObjects(
            WorldFragment fragment
    ) {
        TreeMap<SourceTile, List<WorldObject>> grouped = new TreeMap<>();
        for (WorldObject object : fragment.objects()) {
            SourceTile source = new SourceTile(object.plane(), object.x(), object.y());
            grouped.computeIfAbsent(source, ignored -> new ArrayList<>()).add(object);
        }
        grouped.replaceAll((ignored, values) -> List.copyOf(values));
        return grouped;
    }

    private static List<SourceTile> sourceScope(
            WorldFragmentPastePolicy policy,
            Set<SourceTile> terrain,
            Set<SourceTile> objectAnchors
    ) {
        TreeSet<SourceTile> scope = new TreeSet<>();

        if (policy.terrainMode() == WorldFragmentPastePolicy.TerrainMode.REPLACE) {
            scope.addAll(terrain);
        }

        switch (policy.objectMode()) {
            case PRESERVE -> {
            }
            case MERGE -> scope.addAll(objectAnchors);
            case REPLACE -> {
                // Terrain patches are the fragment's authored tile mask. Using
                // them for replace semantics also clears destination objects
                // where the source fragment intentionally has none.
                scope.addAll(terrain);
                scope.addAll(objectAnchors);
            }
        }

        return List.copyOf(scope);
    }

    private static Integer resolveHeightDelta(
            WorldRegionSessionWindow window,
            WorldFragment fragment,
            int targetX,
            int targetY,
            WorldFragmentPastePolicy policy,
            TreeMap<SourceTile, TerrainTilePatch> terrain,
            List<WorldFragmentPasteResult.Conflict> conflicts
    ) {
        if (policy.terrainMode() != WorldFragmentPastePolicy.TerrainMode.REPLACE
                || policy.heightMode()
                != WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR
                || terrain.isEmpty()) {
            return null;
        }

        SourceTile anchor;
        if (policy.heightAnchor().isPresent()) {
            WorldFragmentPastePolicy.HeightAnchor requested =
                    policy.heightAnchor().orElseThrow();
            anchor = new SourceTile(requested.plane(), requested.x(), requested.y());
        } else {
            anchor = terrain.firstKey();
        }

        TerrainTilePatch sourcePatch = terrain.get(anchor);
        WorldTile destination = translate(fragment, anchor, targetX, targetY);
        if (sourcePatch == null) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.HEIGHT_ANCHOR_UNAVAILABLE,
                    destination,
                    "Selected height anchor does not contain source terrain"));
            return null;
        }

        TileSnapshot destinationSnapshot = readDestination(
                window, destination, conflicts, true);
        if (destinationSnapshot == null) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.HEIGHT_ANCHOR_UNAVAILABLE,
                    destination,
                    "Height-offset paste cannot read the destination anchor"));
            return null;
        }

        return destinationSnapshot.southWestHeight()
                - sourcePatch.snapshot().southWestHeight();
    }

    private static TileSnapshot composeAfter(
            TileSnapshot before,
            TerrainTilePatch sourceTerrain,
            List<WorldObject> sourceObjects,
            LocalTile destinationLocal,
            WorldFragmentPastePolicy policy,
            Integer heightDelta
    ) {
        int sw = before.southWestHeight();
        int se = before.southEastHeight();
        int ne = before.northEastHeight();
        int nw = before.northWestHeight();
        int underlay = before.underlayId();
        int overlay = before.overlayId();
        int shape = before.overlayShape();
        int rotation = before.overlayRotation();
        int flags = before.flags();
        TerrainHeightSource heightSource = before.heightSource();

        boolean terrainAlignmentAvailable =
                policy.heightMode() != WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR
                        || heightDelta != null;
        if (policy.terrainMode() == WorldFragmentPastePolicy.TerrainMode.REPLACE
                && sourceTerrain != null
                && terrainAlignmentAvailable) {
            TileSnapshot source = sourceTerrain.snapshot();
            underlay = source.underlayId();
            overlay = source.overlayId();
            shape = source.overlayShape();
            rotation = source.overlayRotation();
            flags = source.flags();

            switch (policy.heightMode()) {
                case PRESERVE_DESTINATION -> {
                    // Keep both destination heights and their provenance.
                }
                case SOURCE_ABSOLUTE -> {
                    sw = source.southWestHeight();
                    se = source.southEastHeight();
                    ne = source.northEastHeight();
                    nw = source.northWestHeight();
                    heightSource = TerrainHeightSource.authoredSource();
                }
                case OFFSET_FROM_ANCHOR -> {
                    if (heightDelta == null) {
                        // The planner reports the unavailable anchor. Preserve
                        // destination heights rather than inventing a delta.
                        break;
                    }
                    sw = Math.addExact(source.southWestHeight(), heightDelta);
                    se = Math.addExact(source.southEastHeight(), heightDelta);
                    ne = Math.addExact(source.northEastHeight(), heightDelta);
                    nw = Math.addExact(source.northWestHeight(), heightDelta);
                    heightSource = TerrainHeightSource.authoredSource();
                }
            }
        }

        List<WorldObject> objects = composeObjects(
                before.objects(), sourceObjects, destinationLocal, policy);

        return new TileSnapshot(
                sw, se, ne, nw,
                underlay, overlay, shape, rotation, flags,
                objects, heightSource);
    }

    private static List<WorldObject> composeObjects(
            List<WorldObject> destination,
            List<WorldObject> source,
            LocalTile destinationLocal,
            WorldFragmentPastePolicy policy
    ) {
        if (policy.objectMode() == WorldFragmentPastePolicy.ObjectMode.PRESERVE) {
            return destination;
        }

        List<WorldObject> localized = new ArrayList<>();
        for (WorldObject object : source) {
            if (!policy.includesObjectType(object.type())) continue;
            localized.add(new WorldObject(
                    object.id(),
                    object.type(),
                    object.rotation(),
                    destinationLocal.plane(),
                    destinationLocal.x(),
                    destinationLocal.y()));
        }

        if (policy.objectMode() == WorldFragmentPastePolicy.ObjectMode.MERGE) {
            LinkedHashSet<WorldObject> merged = new LinkedHashSet<>(destination);
            merged.addAll(localized);
            return List.copyOf(merged);
        }

        if (policy.objectTypes().isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(localized));
        }

        List<WorldObject> replacedCategories = new ArrayList<>();
        for (WorldObject object : destination) {
            if (!policy.includesObjectType(object.type())) {
                replacedCategories.add(object);
            }
        }
        replacedCategories.addAll(localized);
        return List.copyOf(new LinkedHashSet<>(replacedCategories));
    }

    private static ResolvedDestination resolveEditable(
            WorldRegionSessionWindow window,
            WorldTile destination,
            List<WorldFragmentPasteResult.Conflict> conflicts
    ) {
        WorldTileAddress address = destination.address();
        EditorSession session = window.session(address.regionId()).orElse(null);
        if (session == null) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                    destination,
                    "The destination OSRS region is not loaded"));
            return null;
        }

        LocalTile local = new LocalTile(
                destination.plane(), address.regionLocalX(), address.regionLocalY());
        if (!session.world().contains(local)) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.PLANE_UNAVAILABLE,
                    destination,
                    "The destination region does not contain this authored plane"));
            return null;
        }

        if (!session.canEdit()) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.READ_ONLY_REGION,
                    destination,
                    "The destination region is inspect-only"));
            return null;
        }

        return new ResolvedDestination(session, local);
    }

    private static TileSnapshot readDestination(
            WorldRegionSessionWindow window,
            WorldTile destination,
            List<WorldFragmentPasteResult.Conflict> conflicts,
            boolean suppressReadOnly
    ) {
        WorldTileAddress address = destination.address();
        EditorSession session = window.session(address.regionId()).orElse(null);
        if (session == null) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                    destination,
                    "The destination OSRS region is not loaded"));
            return null;
        }

        LocalTile local = new LocalTile(
                destination.plane(), address.regionLocalX(), address.regionLocalY());
        if (!session.world().contains(local)) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.PLANE_UNAVAILABLE,
                    destination,
                    "The destination region does not contain this authored plane"));
            return null;
        }

        if (!suppressReadOnly && !session.canEdit()) {
            conflicts.add(new WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.READ_ONLY_REGION,
                    destination,
                    "The destination region is inspect-only"));
            return null;
        }

        return session.world().tile(local).snapshot();
    }

    private static WorldTile translate(
            WorldFragment fragment,
            SourceTile source,
            int targetX,
            int targetY
    ) {
        int relativeX = source.x() - fragment.bounds().minX();
        int relativeY = source.y() - fragment.bounds().minY();
        return new WorldTile(
                source.plane(),
                Math.addExact(targetX, relativeX),
                Math.addExact(targetY, relativeY));
    }

    private record ResolvedDestination(
            EditorSession session,
            LocalTile local
    ) {
    }

    private record SourceTile(int plane, int x, int y)
            implements Comparable<SourceTile> {
        @Override
        public int compareTo(SourceTile other) {
            int byPlane = Integer.compare(plane, other.plane);
            if (byPlane != 0) return byPlane;
            int byX = Integer.compare(x, other.x);
            if (byX != 0) return byX;
            return Integer.compare(y, other.y);
        }
    }
}
