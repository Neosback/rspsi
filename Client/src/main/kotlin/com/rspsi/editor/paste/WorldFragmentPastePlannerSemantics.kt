package com.rspsi.editor.paste

import com.rspsi.editor.EditorSession
import com.rspsi.editor.WorldRegionSessionWindow
import com.rspsi.editor.change.ChangePlan
import com.rspsi.editor.model.LocalTile
import com.rspsi.editor.model.TerrainHeightSource
import com.rspsi.editor.model.TerrainTilePatch
import com.rspsi.editor.model.TileSnapshot
import com.rspsi.editor.model.WorldFragment
import com.rspsi.editor.model.WorldObject
import com.rspsi.editor.model.WorldTile
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.OptionalLong
import java.util.TreeMap
import java.util.TreeSet

/**
 * Kotlin semantic owner for non-destructive cross-region fragment paste planning.
 *
 * The public Java planner remains a minimal static compatibility shell. Planning stays pure:
 * destination snapshots are read into a [ChangePlan], and [WorldRegionSessionWindow] remains the
 * only multi-region mutation boundary.
 */
object WorldFragmentPastePlannerSemantics {
    @JvmStatic
    fun plan(
        window: WorldRegionSessionWindow?,
        fragment: WorldFragment?,
        targetX: Int,
        targetY: Int,
        policy: WorldFragmentPastePolicy?,
        description: String?,
    ): WorldFragmentPasteResult {
        val safeWindow = window ?: throw NullPointerException("window")
        val safeFragment = fragment ?: throw NullPointerException("fragment")
        val safePolicy = policy ?: throw NullPointerException("policy")
        safePolicy.validateFor(safeFragment)

        if (targetX < 0 || targetY < 0) {
            throw IllegalArgumentException("Paste target coordinates cannot be negative")
        }

        val rawDescription = description ?: throw NullPointerException("description")
        val label = javaTrim(rawDescription)
        if (label.isEmpty()) {
            throw IllegalArgumentException("Paste description cannot be blank")
        }

        val conflicts = ArrayList<WorldFragmentPasteResult.Conflict>()
        val terrain = collectTerrain(
            safeFragment,
            targetX,
            targetY,
            conflicts,
        )
        val objects = collectObjects(safeFragment)
        val heightDelta = resolveHeightDelta(
            safeWindow,
            safeFragment,
            targetX,
            targetY,
            safePolicy,
            terrain,
            conflicts,
        )

        val scope = sourceScope(
            safePolicy,
            terrain.keys,
            objects.keys,
        )
        val plan = ChangePlan.builder(label)
            .provenance(
                ChangePlan.Provenance(
                    WorldFragmentPastePlanner.PRODUCER_ID,
                    OptionalLong.empty(),
                    java.util.Map.of(
                        "terrain",
                        safePolicy.terrainMode().name,
                        "objects",
                        safePolicy.objectMode().name,
                        "heights",
                        safePolicy.heightMode().name,
                        "conflicts",
                        safePolicy.conflictMode().name,
                    ),
                ),
            )

        var skipped = 0
        for (source in scope) {
            val destination = translate(
                safeFragment,
                source,
                targetX,
                targetY,
            )
            val resolved = resolveEditable(
                safeWindow,
                destination,
                conflicts,
            )
            if (resolved == null) {
                skipped++
                continue
            }

            val before = resolved.session.world()
                .tile(resolved.local)
                .snapshot()
            val sourceTerrain = terrain[source]
            val sourceObjects = objects[source] ?: java.util.List.of()
            val after = composeAfter(
                before,
                sourceTerrain,
                sourceObjects,
                resolved.local,
                safePolicy,
                heightDelta,
            )
            plan.setTile(destination, before, after)
        }

        if (conflicts.isNotEmpty()) {
            if (safePolicy.conflictMode() == WorldFragmentPastePolicy.ConflictMode.SKIP) {
                plan.addDiagnostic(
                    "Skipped " + skipped +
                        " destination tile(s) with paste conflicts",
                )
            } else {
                plan.addDiagnostic(
                    "Paste has " + conflicts.size +
                        " unresolved planning conflict(s)",
                )
            }
        }

        return WorldFragmentPasteResult(
            plan.build(),
            conflicts,
            safePolicy.conflictMode(),
        )
    }

    /**
     * Retains java.lang.String.trim() semantics instead of Kotlin's broader Unicode whitespace
     * trimming so description validation does not change as a side effect of the migration.
     */
    private fun javaTrim(value: String): String {
        var start = 0
        var end = value.length
        while (start < end && value[start].code <= 0x20) {
            start++
        }
        while (end > start && value[end - 1].code <= 0x20) {
            end--
        }
        return if (start == 0 && end == value.length) {
            value
        } else {
            value.substring(start, end)
        }
    }

    private fun collectTerrain(
        fragment: WorldFragment,
        targetX: Int,
        targetY: Int,
        conflicts: MutableList<WorldFragmentPasteResult.Conflict>,
    ): TreeMap<SourceTile, TerrainTilePatch> {
        val terrain = TreeMap<SourceTile, TerrainTilePatch>()
        for (patch in fragment.terrain()) {
            if (patch.snapshot.objects().isNotEmpty()) {
                throw IllegalArgumentException(
                    "WorldFragment terrain snapshots must not embed objects; " +
                        "use fragment.objects()",
                )
            }

            val source = SourceTile(
                patch.plane,
                patch.x,
                patch.y,
            )
            val existing = terrain.putIfAbsent(source, patch)
            if (existing != null) {
                conflicts.add(
                    WorldFragmentPasteResult.Conflict(
                        WorldFragmentPasteResult.ConflictCode.DUPLICATE_SOURCE_TILE,
                        translate(fragment, source, targetX, targetY),
                        "Fragment contains more than one terrain patch for the same source tile",
                    ),
                )
            }
        }
        return terrain
    }

    private fun collectObjects(
        fragment: WorldFragment,
    ): TreeMap<SourceTile, List<WorldObject>> {
        val mutable = TreeMap<SourceTile, MutableList<WorldObject>>()
        for (objectPlacement in fragment.objects()) {
            val source = SourceTile(
                objectPlacement.plane,
                objectPlacement.x,
                objectPlacement.y,
            )
            mutable.computeIfAbsent(source) { ArrayList() }
                .add(objectPlacement)
        }

        val grouped = TreeMap<SourceTile, List<WorldObject>>()
        for ((source, values) in mutable) {
            grouped[source] = java.util.List.copyOf(values)
        }
        return grouped
    }

    private fun sourceScope(
        policy: WorldFragmentPastePolicy,
        terrain: Set<SourceTile>,
        objectAnchors: Set<SourceTile>,
    ): List<SourceTile> {
        val scope = TreeSet<SourceTile>()

        if (policy.terrainMode() == WorldFragmentPastePolicy.TerrainMode.REPLACE) {
            scope.addAll(terrain)
        }

        when (policy.objectMode()) {
            WorldFragmentPastePolicy.ObjectMode.PRESERVE -> Unit
            WorldFragmentPastePolicy.ObjectMode.MERGE -> scope.addAll(objectAnchors)
            WorldFragmentPastePolicy.ObjectMode.REPLACE -> {
                // Terrain patches are the fragment's authored tile mask. Replace semantics also
                // clear destination objects where the source fragment intentionally has none.
                scope.addAll(terrain)
                scope.addAll(objectAnchors)
            }
        }

        return java.util.List.copyOf(scope)
    }

    private fun resolveHeightDelta(
        window: WorldRegionSessionWindow,
        fragment: WorldFragment,
        targetX: Int,
        targetY: Int,
        policy: WorldFragmentPastePolicy,
        terrain: TreeMap<SourceTile, TerrainTilePatch>,
        conflicts: MutableList<WorldFragmentPasteResult.Conflict>,
    ): Int? {
        if (
            policy.terrainMode() != WorldFragmentPastePolicy.TerrainMode.REPLACE ||
            policy.heightMode() != WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR ||
            terrain.isEmpty()
        ) {
            return null
        }

        val anchor =
            if (policy.heightAnchor().isPresent) {
                val requested = policy.heightAnchor().orElseThrow()
                SourceTile(
                    requested.plane(),
                    requested.x(),
                    requested.y(),
                )
            } else {
                terrain.firstKey()
            }

        val sourcePatch = terrain[anchor]
        val destination = translate(
            fragment,
            anchor,
            targetX,
            targetY,
        )
        if (sourcePatch == null) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.HEIGHT_ANCHOR_UNAVAILABLE,
                    destination,
                    "Selected height anchor does not contain source terrain",
                ),
            )
            return null
        }

        val destinationSnapshot = readDestination(
            window,
            destination,
            conflicts,
            suppressReadOnly = true,
        )
        if (destinationSnapshot == null) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.HEIGHT_ANCHOR_UNAVAILABLE,
                    destination,
                    "Height-offset paste cannot read the destination anchor",
                ),
            )
            return null
        }

        return destinationSnapshot.southWestHeight() -
            sourcePatch.snapshot.southWestHeight()
    }

    private fun composeAfter(
        before: TileSnapshot,
        sourceTerrain: TerrainTilePatch?,
        sourceObjects: List<WorldObject>,
        destinationLocal: LocalTile,
        policy: WorldFragmentPastePolicy,
        heightDelta: Int?,
    ): TileSnapshot {
        var sw = before.southWestHeight()
        var se = before.southEastHeight()
        var ne = before.northEastHeight()
        var nw = before.northWestHeight()
        var underlay = before.underlayId()
        var overlay = before.overlayId()
        var shape = before.overlayShape()
        var rotation = before.overlayRotation()
        var flags = before.flags()
        var heightSource = before.heightSource()

        val terrainAlignmentAvailable =
            policy.heightMode() != WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR ||
                heightDelta != null
        if (
            policy.terrainMode() == WorldFragmentPastePolicy.TerrainMode.REPLACE &&
            sourceTerrain != null &&
            terrainAlignmentAvailable
        ) {
            val source = sourceTerrain.snapshot
            underlay = source.underlayId()
            overlay = source.overlayId()
            shape = source.overlayShape()
            rotation = source.overlayRotation()
            flags = source.flags()

            when (policy.heightMode()) {
                WorldFragmentPastePolicy.HeightMode.PRESERVE_DESTINATION -> {
                    // Keep both destination heights and their provenance.
                }

                WorldFragmentPastePolicy.HeightMode.SOURCE_ABSOLUTE -> {
                    sw = source.southWestHeight()
                    se = source.southEastHeight()
                    ne = source.northEastHeight()
                    nw = source.northWestHeight()
                    heightSource = TerrainHeightSource.authoredSource()
                }

                WorldFragmentPastePolicy.HeightMode.OFFSET_FROM_ANCHOR -> {
                    if (heightDelta != null) {
                        sw = Math.addExact(source.southWestHeight(), heightDelta)
                        se = Math.addExact(source.southEastHeight(), heightDelta)
                        ne = Math.addExact(source.northEastHeight(), heightDelta)
                        nw = Math.addExact(source.northWestHeight(), heightDelta)
                        heightSource = TerrainHeightSource.authoredSource()
                    }
                }
            }
        }

        val objects = composeObjects(
            before.objects(),
            sourceObjects,
            destinationLocal,
            policy,
        )

        return TileSnapshot(
            sw,
            se,
            ne,
            nw,
            underlay,
            overlay,
            shape,
            rotation,
            flags,
            objects,
            heightSource,
        )
    }

    private fun composeObjects(
        destination: List<WorldObject>,
        source: List<WorldObject>,
        destinationLocal: LocalTile,
        policy: WorldFragmentPastePolicy,
    ): List<WorldObject> {
        if (policy.objectMode() == WorldFragmentPastePolicy.ObjectMode.PRESERVE) {
            return destination
        }

        val localized = ArrayList<WorldObject>()
        for (objectPlacement in source) {
            if (!policy.includesObjectType(objectPlacement.type)) {
                continue
            }
            localized.add(
                WorldObject(
                    objectPlacement.id,
                    objectPlacement.type,
                    objectPlacement.rotation,
                    destinationLocal.plane,
                    destinationLocal.x,
                    destinationLocal.y,
                ),
            )
        }

        if (policy.objectMode() == WorldFragmentPastePolicy.ObjectMode.MERGE) {
            val merged = LinkedHashSet(destination)
            merged.addAll(localized)
            return java.util.List.copyOf(merged)
        }

        if (policy.objectTypes().isEmpty()) {
            return java.util.List.copyOf(LinkedHashSet(localized))
        }

        val replacedCategories = ArrayList<WorldObject>()
        for (objectPlacement in destination) {
            if (!policy.includesObjectType(objectPlacement.type)) {
                replacedCategories.add(objectPlacement)
            }
        }
        replacedCategories.addAll(localized)
        return java.util.List.copyOf(LinkedHashSet(replacedCategories))
    }

    private fun resolveEditable(
        window: WorldRegionSessionWindow,
        destination: WorldTile,
        conflicts: MutableList<WorldFragmentPasteResult.Conflict>,
    ): ResolvedDestination? {
        val address = destination.address()
        val session = window.session(address.regionId).orElse(null)
        if (session == null) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                    destination,
                    "The destination OSRS region is not loaded",
                ),
            )
            return null
        }

        val local = LocalTile(
            destination.plane,
            address.regionLocalX,
            address.regionLocalY,
        )
        if (!session.world().contains(local)) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.PLANE_UNAVAILABLE,
                    destination,
                    "The destination region does not contain this authored plane",
                ),
            )
            return null
        }

        if (!session.canEdit()) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.READ_ONLY_REGION,
                    destination,
                    "The destination region is inspect-only",
                ),
            )
            return null
        }

        return ResolvedDestination(session, local)
    }

    private fun readDestination(
        window: WorldRegionSessionWindow,
        destination: WorldTile,
        conflicts: MutableList<WorldFragmentPasteResult.Conflict>,
        suppressReadOnly: Boolean,
    ): TileSnapshot? {
        val address = destination.address()
        val session = window.session(address.regionId).orElse(null)
        if (session == null) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.UNLOADED_REGION,
                    destination,
                    "The destination OSRS region is not loaded",
                ),
            )
            return null
        }

        val local = LocalTile(
            destination.plane,
            address.regionLocalX,
            address.regionLocalY,
        )
        if (!session.world().contains(local)) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.PLANE_UNAVAILABLE,
                    destination,
                    "The destination region does not contain this authored plane",
                ),
            )
            return null
        }

        if (!suppressReadOnly && !session.canEdit()) {
            conflicts.add(
                WorldFragmentPasteResult.Conflict(
                    WorldFragmentPasteResult.ConflictCode.READ_ONLY_REGION,
                    destination,
                    "The destination region is inspect-only",
                ),
            )
            return null
        }

        return session.world()
            .tile(local)
            .snapshot()
    }

    private fun translate(
        fragment: WorldFragment,
        source: SourceTile,
        targetX: Int,
        targetY: Int,
    ): WorldTile {
        val relativeX = source.x - fragment.bounds().minX
        val relativeY = source.y - fragment.bounds().minY
        return WorldTile(
            source.plane,
            Math.addExact(targetX, relativeX),
            Math.addExact(targetY, relativeY),
        )
    }

    private data class ResolvedDestination(
        val session: EditorSession,
        val local: LocalTile,
    )

    private data class SourceTile(
        val plane: Int,
        val x: Int,
        val y: Int,
    ) : Comparable<SourceTile> {
        override fun compareTo(other: SourceTile): Int {
            val byPlane = plane.compareTo(other.plane)
            if (byPlane != 0) {
                return byPlane
            }
            val byX = x.compareTo(other.x)
            if (byX != 0) {
                return byX
            }
            return y.compareTo(other.y)
        }
    }
}
