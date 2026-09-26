package com.rspsi.editor.transform

import com.rspsi.editor.model.TerrainTilePatch
import com.rspsi.editor.model.TileBounds
import com.rspsi.editor.model.TileSnapshot
import com.rspsi.editor.model.WorldFragment
import com.rspsi.editor.model.WorldObject
import com.rspsi.osrs.rules.loc.LocPlacementRules
import com.rspsi.osrs.rules.tile.TileShapeRules
import java.util.ArrayList
import java.util.LinkedHashSet

/**
 * Kotlin semantic owner for orthogonal world-fragment transforms.
 *
 * The public Java shell is retained only for the historical static API and package-private
 * transformCorners visibility. Transform math, validation, terrain projection, object-footprint
 * handling, and diagnostics live here.
 */
object WorldFragmentTransformerSemantics {
    private const val MODEL_MIRROR_WARNING =
        "OSRS map locations have quarter-turn rotation but no generic model-mirror bit; " +
            "the fragment layout and placement orientation were reflected, while " +
            "asymmetric model chirality may remain visually unmirrored."

    @JvmStatic
    fun transform(
        fragment: WorldFragment?,
        transform: WorldFragmentTransform?,
        footprints: ObjectFootprintResolver?,
    ): WorldFragmentTransformResult {
        val safeFragment = fragment ?: throw NullPointerException("fragment")
        val safeTransform = transform ?: throw NullPointerException("transform")
        val safeFootprints = footprints ?: throw NullPointerException("footprints")

        val sourceBounds = safeFragment.bounds()
        validatePivot(sourceBounds, safeTransform)

        val dimensions = transformedDimensions(
            sourceBounds.width(),
            sourceBounds.height(),
            safeTransform.quarterTurns,
        )
        val origin = targetOrigin(sourceBounds, safeTransform, dimensions)
        val diagnostics = ArrayList<WorldFragmentTransformResult.Diagnostic>()

        if (
            safeTransform.mirrorX.xor(safeTransform.mirrorY) &&
            safeFragment.objects().isNotEmpty()
        ) {
            diagnostics.add(
                WorldFragmentTransformResult.Diagnostic(
                    WorldFragmentTransformResult.DiagnosticCode.OBJECT_MODEL_MIRROR_NOT_NATIVE,
                    MODEL_MIRROR_WARNING,
                ),
            )
        }

        val terrain = transformTerrain(
            safeFragment,
            sourceBounds,
            origin,
            safeTransform,
        )
        val objects = transformObjects(
            safeFragment,
            sourceBounds,
            origin,
            safeTransform,
            safeFootprints,
            diagnostics,
        )

        val targetBounds = TileBounds(
            origin.x,
            origin.y,
            origin.x + dimensions.width - 1,
            origin.y + dimensions.height - 1,
        )
        return WorldFragmentTransformResult(
            WorldFragment(targetBounds, terrain, objects),
            diagnostics,
        )
    }

    private fun transformTerrain(
        fragment: WorldFragment,
        sourceBounds: TileBounds,
        origin: Origin,
        transform: WorldFragmentTransform,
    ): List<TerrainTilePatch> {
        val terrain = ArrayList<TerrainTilePatch>(fragment.terrain().size)
        val terrainCoordinates = LinkedHashSet<Long>()

        for (patch in fragment.terrain()) {
            if (patch.snapshot.objects().isNotEmpty()) {
                throw IllegalArgumentException(
                    "WorldFragment terrain snapshots must not embed objects; use fragment.objects()",
                )
            }

            val target = transformLocal(
                patch.x - sourceBounds.minX,
                patch.y - sourceBounds.minY,
                sourceBounds.width(),
                sourceBounds.height(),
                transform,
            )
            val worldX = origin.x + target.x
            val worldY = origin.y + target.y
            val key = pack(patch.plane, worldX, worldY)
            if (!terrainCoordinates.add(key)) {
                throw IllegalStateException(
                    "Fragment transform produced duplicate terrain coordinates",
                )
            }

            terrain.add(
                TerrainTilePatch(
                    patch.plane,
                    worldX,
                    worldY,
                    transformSnapshot(patch.snapshot, transform),
                ),
            )
        }
        return terrain
    }

    private fun transformObjects(
        fragment: WorldFragment,
        sourceBounds: TileBounds,
        origin: Origin,
        transform: WorldFragmentTransform,
        footprints: ObjectFootprintResolver,
        diagnostics: MutableList<WorldFragmentTransformResult.Diagnostic>,
    ): List<WorldObject> {
        val objects = ArrayList<WorldObject>(fragment.objects().size)
        for (objectPlacement in fragment.objects()) {
            val resolved = footprints.resolve(objectPlacement)
            val footprint = resolved?.orElseThrow {
                IllegalArgumentException(
                    "Missing object footprint for object #" + objectPlacement.id,
                )
            } ?: throw NullPointerException()

            objects.add(
                transformObject(
                    objectPlacement,
                    footprint,
                    sourceBounds,
                    origin,
                    transform,
                    diagnostics,
                ),
            )
        }
        return objects
    }

    private fun transformSnapshot(
        source: TileSnapshot,
        transform: WorldFragmentTransform,
    ): TileSnapshot {
        val heights = transformCorners(
            source.southWestHeight(),
            source.southEastHeight(),
            source.northEastHeight(),
            source.northWestHeight(),
            transform,
        )
        val overlay = TileShapeRules.transformOverlay(
            source.overlayShape(),
            source.overlayRotation(),
            transform.mirrorX,
            transform.mirrorY,
            transform.quarterTurns,
        )

        return TileSnapshot(
            heights[0],
            heights[1],
            heights[2],
            heights[3],
            source.underlayId(),
            source.overlayId(),
            overlay.shape(),
            overlay.rotation(),
            source.flags(),
            java.util.List.of(),
            source.heightSource(),
        )
    }

    private fun transformObject(
        source: WorldObject,
        footprint: ObjectFootprintResolver.ObjectFootprint,
        bounds: TileBounds,
        origin: Origin,
        transform: WorldFragmentTransform,
        diagnostics: MutableList<WorldFragmentTransformResult.Diagnostic>,
    ): WorldObject {
        val orientedWidth = LocPlacementRules.rotatedWidth(
            footprint.width,
            footprint.length,
            source.rotation,
        )
        val orientedLength = LocPlacementRules.rotatedLength(
            footprint.width,
            footprint.length,
            source.rotation,
        )

        val localX = source.x - bounds.minX
        val localY = source.y - bounds.minY
        if (
            localX < 0 ||
            localY < 0 ||
            localX + orientedWidth > bounds.width() ||
            localY + orientedLength > bounds.height()
        ) {
            throw IllegalArgumentException(
                "Object #" + source.id + " footprint extends outside fragment bounds",
            )
        }

        val corners = listOf(
            transformLocal(localX, localY, bounds.width(), bounds.height(), transform),
            transformLocal(
                localX + orientedWidth - 1,
                localY,
                bounds.width(),
                bounds.height(),
                transform,
            ),
            transformLocal(
                localX,
                localY + orientedLength - 1,
                bounds.width(),
                bounds.height(),
                transform,
            ),
            transformLocal(
                localX + orientedWidth - 1,
                localY + orientedLength - 1,
                bounds.width(),
                bounds.height(),
                transform,
            ),
        )

        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val maxX = corners.maxOf { it.x }
        val maxY = corners.maxOf { it.y }
        val targetFootprintWidth = maxX - minX + 1
        val targetFootprintLength = maxY - minY + 1

        val semanticRotation = LocPlacementRules.transformOrientation(
            source.type,
            source.rotation,
            transform.mirrorX,
            transform.mirrorY,
            transform.quarterTurns,
        )
        val targetRotation = chooseRepresentableRotation(
            footprint,
            semanticRotation,
            targetFootprintWidth,
            targetFootprintLength,
        )

        if (targetRotation != semanticRotation) {
            diagnostics.add(
                WorldFragmentTransformResult.Diagnostic(
                    WorldFragmentTransformResult.DiagnosticCode.OBJECT_ORIENTATION_APPROXIMATED,
                    "Object #" + source.id + " type " + source.type +
                        " cannot preserve both the semantic mirror orientation and its " +
                        targetFootprintWidth + "x" + targetFootprintLength +
                        " occupied footprint; rotation " + targetRotation +
                        " preserves the mirrored footprint.",
                ),
            )
        }

        return WorldObject(
            source.id,
            source.type,
            targetRotation,
            source.plane,
            origin.x + minX,
            origin.y + minY,
        )
    }

    private fun chooseRepresentableRotation(
        footprint: ObjectFootprintResolver.ObjectFootprint,
        preferredRotation: Int,
        targetWidth: Int,
        targetLength: Int,
    ): Int {
        if (matchesFootprint(footprint, preferredRotation, targetWidth, targetLength)) {
            return preferredRotation
        }

        // The opposite orientation has the same footprint and keeps the closest quarter-turn
        // facing available in the OSRS map format.
        val opposite = (preferredRotation + 2) and 3
        if (matchesFootprint(footprint, opposite, targetWidth, targetLength)) {
            return opposite
        }

        return (0..3).firstOrNull { rotation ->
            matchesFootprint(footprint, rotation, targetWidth, targetLength)
        } ?: throw IllegalStateException(
            "No OSRS orientation can represent transformed object footprint " +
                targetWidth + "x" + targetLength,
        )
    }

    private fun matchesFootprint(
        footprint: ObjectFootprintResolver.ObjectFootprint,
        rotation: Int,
        width: Int,
        length: Int,
    ): Boolean =
        LocPlacementRules.rotatedWidth(
            footprint.width,
            footprint.length,
            rotation,
        ) == width &&
            LocPlacementRules.rotatedLength(
                footprint.width,
                footprint.length,
                rotation,
            ) == length

    /** Returns transformed SW, SE, NE, NW heights. */
    @JvmStatic
    fun transformCorners(
        southWest: Int,
        southEast: Int,
        northEast: Int,
        northWest: Int,
        transform: WorldFragmentTransform?,
    ): IntArray {
        val safeTransform = transform ?: throw NullPointerException("transform")
        val source = intArrayOf(southWest, southEast, northEast, northWest)
        val coordinates = arrayOf(
            intArrayOf(0, 0),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(0, 1),
        )
        val target = IntArray(4)

        for (index in source.indices) {
            val point = transformLocal(
                coordinates[index][0],
                coordinates[index][1],
                2,
                2,
                safeTransform,
            )
            target[cornerIndex(point.x, point.y)] = source[index]
        }
        return target
    }

    private fun cornerIndex(
        x: Int,
        y: Int,
    ): Int =
        when {
            x == 0 && y == 0 -> 0
            x == 1 && y == 0 -> 1
            x == 1 && y == 1 -> 2
            x == 0 && y == 1 -> 3
            else -> throw IllegalArgumentException(
                "Not a tile corner: " + x + "," + y,
            )
        }

    private fun transformLocal(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        transform: WorldFragmentTransform,
    ): Point {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw IndexOutOfBoundsException(
                "Local coordinate is outside fragment grid: " + x + "," + y,
            )
        }

        val transformedX = if (transform.mirrorX) width - 1 - x else x
        val transformedY = if (transform.mirrorY) height - 1 - y else y
        return when (transform.quarterTurns) {
            0 -> Point(transformedX, transformedY)
            1 -> Point(transformedY, width - 1 - transformedX)
            2 -> Point(width - 1 - transformedX, height - 1 - transformedY)
            3 -> Point(height - 1 - transformedY, transformedX)
            else -> throw IllegalStateException("Unexpected quarter-turn value")
        }
    }

    private fun transformedDimensions(
        width: Int,
        height: Int,
        quarterTurns: Int,
    ): Dimensions =
        if ((quarterTurns and 1) == 0) {
            Dimensions(width, height)
        } else {
            Dimensions(height, width)
        }

    private fun targetOrigin(
        sourceBounds: TileBounds,
        transform: WorldFragmentTransform,
        dimensions: Dimensions,
    ): Origin {
        val pivotOption = transform.pivot ?: throw NullPointerException("pivot")
        if (pivotOption.isEmpty()) {
            return Origin(sourceBounds.minX, sourceBounds.minY)
        }

        val pivot = pivotOption.orElseThrow()
        val transformedPivot = transformLocal(
            pivot.x - sourceBounds.minX,
            pivot.y - sourceBounds.minY,
            sourceBounds.width(),
            sourceBounds.height(),
            transform,
        )
        val targetMinX = pivot.x - transformedPivot.x
        val targetMinY = pivot.y - transformedPivot.y
        if (
            targetMinX < 0 ||
            targetMinY < 0 ||
            targetMinX + dimensions.width - 1 < targetMinX ||
            targetMinY + dimensions.height - 1 < targetMinY
        ) {
            throw IllegalArgumentException(
                "Fragment transform would move bounds outside valid world coordinates",
            )
        }
        return Origin(targetMinX, targetMinY)
    }

    private fun validatePivot(
        bounds: TileBounds,
        transform: WorldFragmentTransform,
    ) {
        val pivotOption = transform.pivot ?: throw NullPointerException("pivot")
        if (pivotOption.isEmpty()) {
            return
        }
        val pivot = pivotOption.orElseThrow()
        if (!bounds.contains(pivot.x, pivot.y)) {
            throw IllegalArgumentException("Fragment pivot must be inside source bounds")
        }
    }

    private fun pack(
        plane: Int,
        x: Int,
        y: Int,
    ): Long =
        (plane.toLong() shl 56) xor
            (x.toLong() shl 28) xor
            y.toLong()

    private data class Point(
        val x: Int,
        val y: Int,
    )

    private data class Dimensions(
        val width: Int,
        val height: Int,
    ) {
        init {
            if (width <= 0 || height <= 0) {
                throw IllegalArgumentException(
                    "Transformed fragment dimensions must be positive",
                )
            }
        }
    }

    private data class Origin(
        val x: Int,
        val y: Int,
    ) {
        init {
            if (x < 0 || y < 0) {
                throw IllegalArgumentException("Fragment origin cannot be negative")
            }
        }
    }
}
