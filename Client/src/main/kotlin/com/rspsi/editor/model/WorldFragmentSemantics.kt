package com.rspsi.editor.model

import java.util.ArrayList
import java.util.LinkedHashSet

/**
 * Canonical compatibility semantics for [WorldFragment].
 *
 * [WorldFragment] remains a minimal Java record shell because its compact constructor historically
 * accepts nullable terrain/object lists and replaces them before storing record components.
 * Kotlin JVM records cannot preserve that constructor-normalization behavior exactly, so the Java
 * shell keeps the record ABI while all fragment behavior lives here.
 */
object WorldFragmentSemantics {
    @JvmStatic
    fun requireBounds(bounds: TileBounds?): TileBounds =
        bounds ?: throw NullPointerException("bounds")

    @JvmStatic
    fun normalizeTerrain(terrain: List<TerrainTilePatch>?): List<TerrainTilePatch> =
        java.util.List.copyOf(terrain ?: java.util.List.of())

    @JvmStatic
    fun normalizeObjects(objects: List<WorldObject>?): List<WorldObject> =
        java.util.List.copyOf(objects ?: java.util.List.of())

    @JvmStatic
    fun validateContents(
        bounds: TileBounds,
        terrain: List<TerrainTilePatch>,
        objects: List<WorldObject>,
    ) {
        for (patch in terrain) {
            if (!bounds.contains(patch.x, patch.y)) {
                throw IllegalArgumentException("Terrain patch is outside fragment bounds")
            }
        }
        for (objectPlacement in objects) {
            if (!bounds.contains(objectPlacement.x, objectPlacement.y)) {
                throw IllegalArgumentException("Object is outside fragment bounds")
            }
        }
    }

    /** Captures every document plane in the inclusive selection rectangle. */
    @JvmStatic
    fun capture(
        document: WorldDocument?,
        bounds: TileBounds?,
    ): WorldFragment {
        val safeDocument = document ?: throw NullPointerException("document")
        val safeBounds = bounds ?: throw NullPointerException("bounds")
        if (
            safeBounds.maxX >= safeDocument.width() ||
            safeBounds.maxY >= safeDocument.length()
        ) {
            throw IllegalArgumentException("Fragment bounds exceed document dimensions")
        }

        val patches = ArrayList<TerrainTilePatch>()
        val objects = LinkedHashSet<WorldObject>()
        for (plane in 0 until safeDocument.planes()) {
            for (x in safeBounds.minX..safeBounds.maxX) {
                for (y in safeBounds.minY..safeBounds.maxY) {
                    val source = safeDocument.tile(plane, x, y).snapshot()
                    objects.addAll(source.objects())
                    patches.add(
                        TerrainTilePatch(
                            plane,
                            x,
                            y,
                            withoutObjects(source),
                        ),
                    )
                }
            }
        }

        return WorldFragment(
            safeBounds,
            patches,
            ArrayList(objects),
        )
    }

    /**
     * Terrain capture deliberately strips locations and provenance from each terrain patch.
     *
     * Locations are stored once in the fragment-level object list, matching historical fragment
     * capture and preventing location duplication during paste/transform operations.
     */
    private fun withoutObjects(source: TileSnapshot): TileSnapshot =
        TileSnapshot(
            source.southWestHeight(),
            source.southEastHeight(),
            source.northEastHeight(),
            source.northWestHeight(),
            source.underlayId(),
            source.overlayId(),
            source.overlayShape(),
            source.overlayRotation(),
            source.flags(),
            java.util.List.of(),
        )
}
