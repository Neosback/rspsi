package com.rspsi.editor.paste

import com.rspsi.editor.model.WorldFragment
import java.util.LinkedHashSet
import java.util.Optional

/**
 * Canonical behavior for [WorldFragmentPastePolicy].
 *
 * The public policy remains a minimal Java record shell because its compact constructor
 * defensively replaces the object-type set before record components are stored. Kotlin JVM
 * records cannot preserve that normalization exactly without changing observable construction.
 */
object WorldFragmentPastePolicySemantics {
    @JvmStatic
    fun requireTerrainMode(
        mode: WorldFragmentPastePolicy.TerrainMode?,
    ): WorldFragmentPastePolicy.TerrainMode =
        mode ?: throw NullPointerException("terrainMode")

    @JvmStatic
    fun requireObjectMode(
        mode: WorldFragmentPastePolicy.ObjectMode?,
    ): WorldFragmentPastePolicy.ObjectMode =
        mode ?: throw NullPointerException("objectMode")

    @JvmStatic
    fun requireHeightMode(
        mode: WorldFragmentPastePolicy.HeightMode?,
    ): WorldFragmentPastePolicy.HeightMode =
        mode ?: throw NullPointerException("heightMode")

    @JvmStatic
    fun requireConflictMode(
        mode: WorldFragmentPastePolicy.ConflictMode?,
    ): WorldFragmentPastePolicy.ConflictMode =
        mode ?: throw NullPointerException("conflictMode")

    @JvmStatic
    fun requireHeightAnchor(
        anchor: Optional<WorldFragmentPastePolicy.HeightAnchor>?,
    ): Optional<WorldFragmentPastePolicy.HeightAnchor> =
        anchor ?: throw NullPointerException("heightAnchor")

    /**
     * Produces the historical immutable object-type filter.
     *
     * A [LinkedHashSet] is used first to preserve duplicate collapse and validation order before
     * the result is exposed through Java's immutable [Set.copyOf] representation.
     */
    @JvmStatic
    fun copyObjectTypes(types: Set<Int>?): Set<Int> {
        val source = types ?: throw NullPointerException("objectTypes")
        val validated = LinkedHashSet(source)
        for (type in validated) {
            if (type !in 0..22) {
                throw IllegalArgumentException(
                    "OSRS location type filter must be between 0 and 22: " + type,
                )
            }
        }
        return java.util.Set.copyOf(validated)
    }

    @JvmStatic
    fun replaceAll(): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            WorldFragmentPastePolicy.TerrainMode.REPLACE,
            WorldFragmentPastePolicy.ObjectMode.REPLACE,
            WorldFragmentPastePolicy.HeightMode.SOURCE_ABSOLUTE,
            WorldFragmentPastePolicy.ConflictMode.REPORT,
            Optional.empty(),
            java.util.Set.of(),
        )

    @JvmStatic
    fun terrainOnly(): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            WorldFragmentPastePolicy.TerrainMode.REPLACE,
            WorldFragmentPastePolicy.ObjectMode.PRESERVE,
            WorldFragmentPastePolicy.HeightMode.SOURCE_ABSOLUTE,
            WorldFragmentPastePolicy.ConflictMode.REPORT,
            Optional.empty(),
            java.util.Set.of(),
        )

    @JvmStatic
    fun objectsOnly(): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            WorldFragmentPastePolicy.TerrainMode.PRESERVE,
            WorldFragmentPastePolicy.ObjectMode.REPLACE,
            WorldFragmentPastePolicy.HeightMode.PRESERVE_DESTINATION,
            WorldFragmentPastePolicy.ConflictMode.REPORT,
            Optional.empty(),
            java.util.Set.of(),
        )

    @JvmStatic
    fun mergeObjects(): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            WorldFragmentPastePolicy.TerrainMode.PRESERVE,
            WorldFragmentPastePolicy.ObjectMode.MERGE,
            WorldFragmentPastePolicy.HeightMode.PRESERVE_DESTINATION,
            WorldFragmentPastePolicy.ConflictMode.REPORT,
            Optional.empty(),
            java.util.Set.of(),
        )

    @JvmStatic
    fun withHeightMode(
        policy: WorldFragmentPastePolicy,
        mode: WorldFragmentPastePolicy.HeightMode?,
    ): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            policy.terrainMode(),
            policy.objectMode(),
            mode,
            policy.conflictMode(),
            policy.heightAnchor(),
            policy.objectTypes(),
        )

    @JvmStatic
    fun withConflictMode(
        policy: WorldFragmentPastePolicy,
        mode: WorldFragmentPastePolicy.ConflictMode?,
    ): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            policy.terrainMode(),
            policy.objectMode(),
            policy.heightMode(),
            mode,
            policy.heightAnchor(),
            policy.objectTypes(),
        )

    @JvmStatic
    fun withHeightAnchor(
        policy: WorldFragmentPastePolicy,
        anchor: WorldFragmentPastePolicy.HeightAnchor?,
    ): WorldFragmentPastePolicy {
        val safeAnchor = anchor ?: throw NullPointerException("anchor")
        return WorldFragmentPastePolicy(
            policy.terrainMode(),
            policy.objectMode(),
            policy.heightMode(),
            policy.conflictMode(),
            Optional.of(safeAnchor),
            policy.objectTypes(),
        )
    }

    @JvmStatic
    fun withObjectTypes(
        policy: WorldFragmentPastePolicy,
        types: Set<Int>?,
    ): WorldFragmentPastePolicy =
        WorldFragmentPastePolicy(
            policy.terrainMode(),
            policy.objectMode(),
            policy.heightMode(),
            policy.conflictMode(),
            policy.heightAnchor(),
            types,
        )

    @JvmStatic
    fun includesObjectType(
        policy: WorldFragmentPastePolicy,
        type: Int,
    ): Boolean =
        policy.objectTypes().isEmpty() || policy.objectTypes().contains(type)

    @JvmStatic
    fun validateFor(
        policy: WorldFragmentPastePolicy,
        fragment: WorldFragment?,
    ) {
        val safeFragment = fragment ?: throw NullPointerException("fragment")
        val anchorOption = policy.heightAnchor()
        if (anchorOption.isPresent) {
            val anchor = anchorOption.orElseThrow()
            if (!safeFragment.bounds().contains(anchor.x(), anchor.y())) {
                throw IllegalArgumentException(
                    "Paste height anchor must be inside fragment bounds",
                )
            }
        }
    }

    @JvmStatic
    fun validateHeightAnchor(
        plane: Int,
        x: Int,
        y: Int,
    ) {
        if (plane < 0 || x < 0 || y < 0) {
            throw IllegalArgumentException("Paste height anchor cannot be negative")
        }
    }
}
