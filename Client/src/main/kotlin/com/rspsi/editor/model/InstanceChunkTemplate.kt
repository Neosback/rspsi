package com.rspsi.editor.model

import java.util.Optional

/**
 * Neutral representation of one RuneScape instance-template entry.
 *
 * Packed layout matches the client-facing OSRS template format:
 * rotation in bits 1..2, source Y chunk in bits 3..13, source X chunk in bits 14..23,
 * and source plane in bits 24..25. [ABSENT] remains the -1 sentinel.
 */
@JvmRecord
data class InstanceChunkTemplate(
    val targetPlane: Int,
    val sceneChunkX: Int,
    val sceneChunkY: Int,
    val sourcePlane: Int,
    val sourceChunkX: Int,
    val sourceChunkY: Int,
    val rotation: Int,
) {
    init {
        if (
            targetPlane < 0 ||
            sceneChunkX < 0 ||
            sceneChunkY < 0 ||
            sourcePlane < 0 ||
            sourceChunkX < 0 ||
            sourceChunkY < 0
        ) {
            throw IllegalArgumentException("Instance chunk coordinates cannot be negative")
        }
        if (targetPlane > 3 || sourcePlane > 3) {
            throw IllegalArgumentException(
                "Instance chunk planes must fit the OSRS 2-bit plane range",
            )
        }
        if (sourceChunkX > 0x3ff || sourceChunkY > 0x7ff) {
            throw IllegalArgumentException(
                "Instance source chunk coordinates exceed the OSRS packed layout",
            )
        }
        if (rotation !in 0..3) {
            throw IllegalArgumentException("Instance chunk rotation must be between 0 and 3")
        }
    }

    /** Encodes this template using the current OSRS client bit layout. */
    fun encode(): Int =
        (rotation shl 1) or
            (sourceChunkY shl 3) or
            (sourceChunkX shl 14) or
            (sourcePlane shl 24)

    fun sourceOriginX(): Int = sourceChunkX * CHUNK_SIZE

    fun sourceOriginY(): Int = sourceChunkY * CHUNK_SIZE

    fun sceneOriginX(sceneBaseX: Int): Int =
        sceneBaseX + sceneChunkX * CHUNK_SIZE

    fun sceneOriginY(sceneBaseY: Int): Int =
        sceneBaseY + sceneChunkY * CHUNK_SIZE

    override fun toString(): String =
        "InstanceChunkTemplate[" +
            "targetPlane=$targetPlane, " +
            "sceneChunkX=$sceneChunkX, " +
            "sceneChunkY=$sceneChunkY, " +
            "sourcePlane=$sourcePlane, " +
            "sourceChunkX=$sourceChunkX, " +
            "sourceChunkY=$sourceChunkY, " +
            "rotation=$rotation]"

    companion object {
        const val CHUNK_SIZE: Int = 8
        const val ABSENT: Int = -1

        /**
         * Decodes a packed template entry, returning empty for [ABSENT].
         *
         * The absent sentinel is intentionally checked before validating target coordinates,
         * matching the historical Java behavior.
         */
        @JvmStatic
        fun decode(
            packed: Int,
            targetPlane: Int,
            sceneChunkX: Int,
            sceneChunkY: Int,
        ): Optional<InstanceChunkTemplate> {
            if (packed == ABSENT) {
                return Optional.empty()
            }
            if (targetPlane < 0 || sceneChunkX < 0 || sceneChunkY < 0) {
                throw IllegalArgumentException("Target instance coordinates cannot be negative")
            }
            return Optional.of(
                InstanceChunkTemplate(
                    targetPlane,
                    sceneChunkX,
                    sceneChunkY,
                    (packed ushr 24) and 0x3,
                    (packed ushr 14) and 0x3ff,
                    (packed ushr 3) and 0x7ff,
                    (packed ushr 1) and 0x3,
                ),
            )
        }
    }
}
