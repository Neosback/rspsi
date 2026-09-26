@file:JvmName("FloorId")

package com.rspsi.editor.model

/**
 * Converts between floor ids as encoded in map tiles and floor-definition ids.
 *
 * Map tiles store `definitionId + 1`, reserving 0 for "no floor". Keeping this conversion in
 * one neutral boundary prevents tools, inspectors, and cache code from each open-coding the
 * offset and disagreeing about the sentinel.
 */

/** Returns the definition id for an encoded map value, or -1 when the tile has no floor. */
fun definitionId(encoded: Int): Int =
    if (encoded > 0) encoded - 1 else -1

/** Returns the encoded map value for a definition id, or 0 for "no floor". */
fun encode(definitionId: Int): Int =
    if (definitionId >= 0) definitionId + 1 else 0
