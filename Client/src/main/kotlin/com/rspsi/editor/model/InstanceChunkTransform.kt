package com.rspsi.editor.model

/**
 * Maps one source 8x8 OSRS map chunk into a scene instance.
 *
 * Source coordinates remain canonical world coordinates. Scene coordinates are world-space
 * coordinates based at the scene base, keeping this transform renderer-neutral.
 *
 * The legacy [TileCoordinate] surface is preserved intentionally in this migration. Replacing
 * that compatibility type with explicit [WorldTile]/scene coordinate contracts is a separate
 * boundary change rather than part of the Java-to-Kotlin conversion.
 */
class InstanceChunkTransform(
    template: InstanceChunkTemplate?,
    private val sceneBaseX: Int,
    private val sceneBaseY: Int,
) {
    private val template: InstanceChunkTemplate =
        template ?: throw NullPointerException("template")

    init {
        if (sceneBaseX < 0 || sceneBaseY < 0) {
            throw IllegalArgumentException("Scene base coordinates cannot be negative")
        }
    }

    fun template(): InstanceChunkTemplate = template

    fun sceneBaseX(): Int = sceneBaseX

    fun sceneBaseY(): Int = sceneBaseY

    /** Maps a source world tile into the target scene tile. */
    fun sourceToScene(source: TileCoordinate?): TileCoordinate {
        val safeSource = source ?: throw NullPointerException("source")
        requireSourceTile(safeSource)
        val localX = safeSource.x - template.sourceOriginX()
        val localY = safeSource.y - template.sourceOriginY()
        val rotated = rotate(localX, localY, template.rotation)
        return TileCoordinate(
            template.targetPlane,
            template.sceneOriginX(sceneBaseX) + rotated[0],
            template.sceneOriginY(sceneBaseY) + rotated[1],
        )
    }

    /** Maps a target scene tile back to its source world tile. */
    fun sceneToSource(scene: TileCoordinate?): TileCoordinate {
        val safeScene = scene ?: throw NullPointerException("scene")
        requireSceneTile(safeScene)
        val localX = safeScene.x - template.sceneOriginX(sceneBaseX)
        val localY = safeScene.y - template.sceneOriginY(sceneBaseY)
        val sourceLocal = rotate(localX, localY, (4 - template.rotation) and 3)
        return TileCoordinate(
            template.sourcePlane,
            template.sourceOriginX() + sourceLocal[0],
            template.sourceOriginY() + sourceLocal[1],
        )
    }

    /** Applies the instance rotation to a location's orientation. */
    fun sourceObjectRotationToScene(sourceRotation: Int): Int {
        if (sourceRotation !in 0..3) {
            throw IllegalArgumentException("Object rotation must be between 0 and 3")
        }
        return (sourceRotation + template.rotation) and 3
    }

    /** Maps an object anchor and orientation while preserving its identity/type. */
    fun sourceObjectToScene(source: WorldObject?): WorldObject =
        sourceObjectToScene(source, 1, 1)

    /** Maps an object anchor using its unrotated definition footprint. */
    fun sourceObjectToScene(
        source: WorldObject?,
        footprintWidth: Int,
        footprintLength: Int,
    ): WorldObject {
        val safeSource = source ?: throw NullPointerException("source")
        if (footprintWidth <= 0 || footprintLength <= 0) {
            throw IllegalArgumentException("Object footprint must be positive")
        }
        requireSourceTile(TileCoordinate(safeSource.plane, safeSource.x, safeSource.y))
        val localX = safeSource.x - template.sourceOriginX()
        val localY = safeSource.y - template.sourceOriginY()
        val rotated = rotateObject(
            localX,
            localY,
            template.rotation,
            footprintWidth,
            footprintLength,
            safeSource.rotation,
        )
        val mapped = TileCoordinate(
            template.targetPlane,
            template.sceneOriginX(sceneBaseX) + rotated[0],
            template.sceneOriginY(sceneBaseY) + rotated[1],
        )
        return WorldObject(
            safeSource.id,
            safeSource.type,
            sourceObjectRotationToScene(safeSource.rotation),
            mapped.plane,
            mapped.x,
            mapped.y,
        )
    }

    fun containsSource(source: TileCoordinate?): Boolean =
        source != null &&
            source.plane == template.sourcePlane &&
            source.x >= template.sourceOriginX() &&
            source.x < template.sourceOriginX() + InstanceChunkTemplate.CHUNK_SIZE &&
            source.y >= template.sourceOriginY() &&
            source.y < template.sourceOriginY() + InstanceChunkTemplate.CHUNK_SIZE

    fun containsScene(scene: TileCoordinate?): Boolean =
        scene != null &&
            scene.plane == template.targetPlane &&
            scene.x >= template.sceneOriginX(sceneBaseX) &&
            scene.x < template.sceneOriginX(sceneBaseX) + InstanceChunkTemplate.CHUNK_SIZE &&
            scene.y >= template.sceneOriginY(sceneBaseY) &&
            scene.y < template.sceneOriginY(sceneBaseY) + InstanceChunkTemplate.CHUNK_SIZE

    private fun requireSourceTile(source: TileCoordinate) {
        if (!containsSource(source)) {
            throw IndexOutOfBoundsException("Source tile is outside instance chunk: $source")
        }
    }

    private fun requireSceneTile(scene: TileCoordinate) {
        if (!containsScene(scene)) {
            throw IndexOutOfBoundsException("Scene tile is outside instance chunk: $scene")
        }
    }

    private fun rotate(
        x: Int,
        y: Int,
        rotation: Int,
    ): IntArray =
        when (rotation and 3) {
            0 -> intArrayOf(x, y)
            1 -> intArrayOf(y, InstanceChunkTemplate.CHUNK_SIZE - 1 - x)
            2 -> intArrayOf(
                InstanceChunkTemplate.CHUNK_SIZE - 1 - x,
                InstanceChunkTemplate.CHUNK_SIZE - 1 - y,
            )
            else -> intArrayOf(InstanceChunkTemplate.CHUNK_SIZE - 1 - y, x)
        }

    /**
     * Mirrors the client object-anchor rotation, including orientation-swapped dimensions.
     */
    private fun rotateObject(
        x: Int,
        y: Int,
        rotation: Int,
        sizeX: Int,
        sizeY: Int,
        orientation: Int,
    ): IntArray {
        var rotatedSizeX = sizeX
        var rotatedSizeY = sizeY
        if (orientation and 1 == 1) {
            val temporary = rotatedSizeX
            rotatedSizeX = rotatedSizeY
            rotatedSizeY = temporary
        }

        return when (rotation and 3) {
            0 -> intArrayOf(x, y)
            1 -> intArrayOf(
                y,
                InstanceChunkTemplate.CHUNK_SIZE - 1 - x - (rotatedSizeX - 1),
            )
            2 -> intArrayOf(
                InstanceChunkTemplate.CHUNK_SIZE - 1 - x - (rotatedSizeX - 1),
                InstanceChunkTemplate.CHUNK_SIZE - 1 - y - (rotatedSizeY - 1),
            )
            else -> intArrayOf(
                InstanceChunkTemplate.CHUNK_SIZE - 1 - y - (rotatedSizeY - 1),
                x,
            )
        }
    }
}
