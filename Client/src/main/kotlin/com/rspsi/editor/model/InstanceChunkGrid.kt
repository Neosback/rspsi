package com.rspsi.editor.model

import java.util.ArrayList
import java.util.Optional

/**
 * Neutral collection of packed instance templates for one scene.
 *
 * The grid keeps the client convention [plane][sceneChunkX][sceneChunkY]. Missing entries remain
 * holes, and repeated source chunks intentionally produce multiple scene mappings.
 */
class InstanceChunkGrid private constructor(
    private val sceneBaseX: Int,
    private val sceneBaseY: Int,
    transforms: List<InstanceChunkTransform>,
) {
    private val transforms: List<InstanceChunkTransform> = java.util.List.copyOf(transforms)

    fun sceneBaseX(): Int = sceneBaseX

    fun sceneBaseY(): Int = sceneBaseY

    fun transforms(): List<InstanceChunkTransform> = transforms

    /** Returns every scene occurrence of a source tile, including duplicates. */
    fun sourceToScene(source: TileCoordinate?): List<TileCoordinate> {
        val safeSource = source ?: throw NullPointerException("source")
        val mapped = transforms
            .asSequence()
            .filter { it.containsSource(safeSource) }
            .map { it.sourceToScene(safeSource) }
            .toList()
        return java.util.List.copyOf(mapped)
    }

    /** Resolves a scene tile to its source tile, or empty when it is a hole. */
    fun sceneToSource(scene: TileCoordinate?): Optional<TileCoordinate> {
        val safeScene = scene ?: throw NullPointerException("scene")
        val transform = transforms.firstOrNull { it.containsScene(safeScene) }
            ?: return Optional.empty()
        return Optional.of(transform.sceneToSource(safeScene))
    }

    companion object {
        /**
         * Decodes a client-style [plane][sceneChunkX][sceneChunkY] template grid.
         *
         * Null nested arrays keep their historical explicit failure messages.
         */
        @JvmStatic
        fun decode(
            packedTemplates: Array<Array<IntArray?>?>?,
            sceneBaseX: Int,
            sceneBaseY: Int,
        ): InstanceChunkGrid {
            val safeTemplates =
                packedTemplates ?: throw NullPointerException("packedTemplates")
            if (sceneBaseX < 0 || sceneBaseY < 0) {
                throw IllegalArgumentException("Scene base coordinates cannot be negative")
            }

            val transforms = ArrayList<InstanceChunkTransform>()
            for (plane in safeTemplates.indices) {
                val row =
                    safeTemplates[plane] ?: throw NullPointerException("packedTemplates[plane]")
                for (sceneChunkX in row.indices) {
                    val column =
                        row[sceneChunkX] ?: throw NullPointerException("packedTemplates[plane][x]")
                    for (sceneChunkY in column.indices) {
                        InstanceChunkTemplate
                            .decode(column[sceneChunkY], plane, sceneChunkX, sceneChunkY)
                            .ifPresent { template ->
                                transforms.add(
                                    InstanceChunkTransform(template, sceneBaseX, sceneBaseY),
                                )
                            }
                    }
                }
            }
            return InstanceChunkGrid(sceneBaseX, sceneBaseY, transforms)
        }
    }
}
