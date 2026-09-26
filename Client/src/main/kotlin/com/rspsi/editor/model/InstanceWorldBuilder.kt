package com.rspsi.editor.model

import java.util.ArrayList

/**
 * Materializes an instance template grid into a canonical editor document.
 *
 * Cache archives and client scene classes stop at [WorldRegionWindow]. This builder applies the
 * 8x8 chunk transform and produces ordinary [WorldDocument] tiles for scene construction and
 * editing. Missing source regions remain holes with the destination document's default tiles.
 */
class InstanceWorldBuilder {
    fun build(
        source: WorldRegionWindow?,
        grid: InstanceChunkGrid?,
        width: Int,
        length: Int,
        planes: Int,
    ): WorldDocument =
        build(
            source,
            grid,
            width,
            length,
            planes,
            InstanceObjectFootprintResolver.unit(),
            InstanceGeneratedHeightProvider.required(),
        )

    /** Materializes an instance using definition-derived object footprints. */
    fun build(
        source: WorldRegionWindow?,
        grid: InstanceChunkGrid?,
        width: Int,
        length: Int,
        planes: Int,
        footprintResolver: InstanceObjectFootprintResolver?,
    ): WorldDocument =
        build(
            source,
            grid,
            width,
            length,
            planes,
            footprintResolver,
            InstanceGeneratedHeightProvider.required(),
        )

    /** Materializes an instance with explicit cache-independent height semantics. */
    fun build(
        source: WorldRegionWindow?,
        grid: InstanceChunkGrid?,
        width: Int,
        length: Int,
        planes: Int,
        footprintResolver: InstanceObjectFootprintResolver?,
        generatedHeightProvider: InstanceGeneratedHeightProvider?,
    ): WorldDocument {
        val safeSource = source ?: throw NullPointerException("source")
        val safeGrid = grid ?: throw NullPointerException("grid")
        val safeFootprintResolver =
            InstanceObjectFootprintResolver.requireNonNull(footprintResolver)
        val safeGeneratedHeightProvider =
            generatedHeightProvider ?: throw NullPointerException("generatedHeightProvider")

        if (width <= 0 || length <= 0 || planes <= 0) {
            throw IllegalArgumentException("Instance document dimensions must be positive")
        }

        val result = WorldDocument(width, length, planes)
        for (transform in safeGrid.transforms()) {
            val template = transform.template()
            if (template.targetPlane >= planes) {
                throw IllegalArgumentException(
                    "Instance target plane is outside destination: " + template.targetPlane,
                )
            }
            copyChunk(
                safeSource,
                safeGrid,
                transform,
                result,
                safeFootprintResolver,
                safeGeneratedHeightProvider,
            )
        }
        return result
    }

    private fun copyChunk(
        source: WorldRegionWindow,
        grid: InstanceChunkGrid,
        transform: InstanceChunkTransform,
        result: WorldDocument,
        footprintResolver: InstanceObjectFootprintResolver,
        generatedHeightProvider: InstanceGeneratedHeightProvider,
    ) {
        val template = transform.template()

        for (localX in 0 until InstanceChunkTemplate.CHUNK_SIZE) {
            for (localY in 0 until InstanceChunkTemplate.CHUNK_SIZE) {
                val sourceX = template.sourceOriginX() + localX
                val sourceY = template.sourceOriginY() + localY
                val sourceTile =
                    source.tileSource(template.sourcePlane, sourceX, sourceY).orElse(null)
                        ?: continue

                val sourceCoordinate =
                    TileCoordinate(template.sourcePlane, sourceX, sourceY)
                val destination = transform.sourceToScene(sourceCoordinate)
                val destinationX = destination.x - grid.sceneBaseX()
                val destinationY = destination.y - grid.sceneBaseY()
                if (
                    destinationX < 0 ||
                    destinationX >= result.width() ||
                    destinationY < 0 ||
                    destinationY >= result.length()
                ) {
                    continue
                }

                val target = result.tile(destination.plane, destinationX, destinationY)
                val rotated = rotate(sourceTile.snapshot, transform)
                target.restore(
                    withSouthWestHeight(
                        rotated,
                        replayedHeight(
                            sourceTile.heightSource,
                            template.targetPlane,
                            result,
                            destinationX,
                            destinationY,
                            sourceX,
                            sourceY,
                            rotated.southWestHeight(),
                            generatedHeightProvider,
                        ),
                    ),
                )
                target.heightSource(sourceTile.heightSource)
            }
        }

        // Add locations only after all terrain tiles have been materialized. A rotated multi-tile
        // anchor can land on a tile processed later by the terrain pass.
        for (localX in 0 until InstanceChunkTemplate.CHUNK_SIZE) {
            for (localY in 0 until InstanceChunkTemplate.CHUNK_SIZE) {
                val sourceX = template.sourceOriginX() + localX
                val sourceY = template.sourceOriginY() + localY
                val sourceTile =
                    source.tile(template.sourcePlane, sourceX, sourceY).orElse(null)
                        ?: continue

                for (objectPlacement in sourceTile.objects()) {
                    val footprint = footprintResolver.resolve(objectPlacement)
                        ?: throw IllegalArgumentException(
                            "Footprint resolver returned null for object " + objectPlacement.id,
                        )

                    val worldObject =
                        regionLocalObjectToWorld(objectPlacement, sourceX, sourceY)
                    val mapped =
                        transform.sourceObjectToScene(
                            worldObject,
                            footprint.width,
                            footprint.length,
                        )
                    val objectX = mapped.x - grid.sceneBaseX()
                    val objectY = mapped.y - grid.sceneBaseY()
                    val placedWidth =
                        if (mapped.rotation and 1 == 1) footprint.length else footprint.width
                    val placedLength =
                        if (mapped.rotation and 1 == 1) footprint.width else footprint.length

                    // The reference scene builder does not add locations whose anchor is on the
                    // outer scene border; those cells are reserved for shared edge geometry.
                    if (
                        mapped.plane < 0 ||
                        mapped.plane >= result.planes() ||
                        objectX <= 0 ||
                        objectX >= result.width() - 1 ||
                        objectY <= 0 ||
                        objectY >= result.length() - 1 ||
                        objectX + placedWidth > result.width() ||
                        objectY + placedLength > result.length()
                    ) {
                        continue
                    }

                    val target = result.tile(mapped.plane, objectX, objectY)
                    val snapshot = target.snapshot()
                    val heightSource = target.heightSource()
                    val objects = ArrayList(snapshot.objects())
                    objects.add(
                        WorldObject(
                            mapped.id,
                            mapped.type,
                            mapped.rotation,
                            mapped.plane,
                            objectX,
                            objectY,
                        ),
                    )
                    target.restore(
                        TileSnapshot(
                            snapshot.southWestHeight(),
                            snapshot.southEastHeight(),
                            snapshot.northEastHeight(),
                            snapshot.northWestHeight(),
                            snapshot.underlayId(),
                            snapshot.overlayId(),
                            snapshot.overlayShape(),
                            snapshot.overlayRotation(),
                            snapshot.flags(),
                            objects,
                        ),
                    )
                    target.heightSource(heightSource)
                }
            }
        }
    }

    /** Region archives store object anchors in 0..63 local coordinates. */
    private fun regionLocalObjectToWorld(
        objectPlacement: WorldObject,
        sourceX: Int,
        sourceY: Int,
    ): WorldObject {
        val regionOriginX = (sourceX shr 6) * WorldRegion.REGION_SIZE
        val regionOriginY = (sourceY shr 6) * WorldRegion.REGION_SIZE
        return WorldObject(
            objectPlacement.id,
            objectPlacement.type,
            objectPlacement.rotation,
            objectPlacement.plane,
            regionOriginX + objectPlacement.x,
            regionOriginY + objectPlacement.y,
        )
    }

    private fun rotate(
        source: TileSnapshot,
        transform: InstanceChunkTransform,
    ): TileSnapshot {
        val rotation = transform.template().rotation
        return TileSnapshot(
            corner(source, rotation, 0),
            corner(source, rotation, 1),
            corner(source, rotation, 2),
            corner(source, rotation, 3),
            source.underlayId(),
            source.overlayId(),
            source.overlayShape(),
            if (source.overlayId() == 0) 0 else (source.overlayRotation() + rotation) and 3,
            source.flags(),
            java.util.List.of(),
        )
    }

    private fun replayedHeight(
        source: TerrainHeightSource,
        targetPlane: Int,
        result: WorldDocument,
        destinationX: Int,
        destinationY: Int,
        sourceX: Int,
        sourceY: Int,
        authoredHeight: Int,
        generatedHeightProvider: InstanceGeneratedHeightProvider,
    ): Int {
        if (!source.cacheEncoded) {
            return authoredHeight
        }

        if (targetPlane == 0) {
            return if (source.generated) {
                generatedHeightProvider.heightAt(sourceX, sourceY)
            } else {
                -source.explicitValue * 8
            }
        }

        val previous =
            result.tile(targetPlane - 1, destinationX, destinationY)
                .snapshot()
                .southWestHeight()
        return previous - if (source.generated) 240 else source.explicitValue * 8
    }

    private fun withSouthWestHeight(
        source: TileSnapshot,
        height: Int,
    ): TileSnapshot =
        TileSnapshot(
            height,
            source.southEastHeight(),
            source.northEastHeight(),
            source.northWestHeight(),
            source.underlayId(),
            source.overlayId(),
            source.overlayShape(),
            source.overlayRotation(),
            source.flags(),
            source.objects(),
        )

    /** Returns the destination corner value: SW=0, SE=1, NE=2, NW=3. */
    private fun corner(
        source: TileSnapshot,
        rotation: Int,
        destinationCorner: Int,
    ): Int {
        // A clockwise chunk transform maps destination corners to source corners
        // [SE, NE, NW, SW]. Repeating that permutation gives the 180- and 270-degree cases and
        // matches the client chunk convention.
        return when ((destinationCorner + rotation) and 3) {
            0 -> source.southWestHeight()
            1 -> source.southEastHeight()
            2 -> source.northEastHeight()
            else -> source.northWestHeight()
        }
    }
}
