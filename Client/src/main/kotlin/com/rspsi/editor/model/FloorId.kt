package com.rspsi.editor.model

/**
 * Converts between floor ids as encoded in map tiles and floor-definition ids.
 *
 * Map tiles store `definitionId + 1`, reserving 0 for "no floor". Keeping this conversion in
 * one neutral boundary prevents tools, inspectors, and cache code from each open-coding the
 * offset and disagreeing about the sentinel.
 *
 * A private constructor plus companion keeps the historical `FloorId.foo(...)` call shape in
 * both Kotlin and Java; [JvmStatic] preserves Java's true static methods during migration.
 */
class FloorId private constructor() {
    companion object {
        /** Returns the definition id for an encoded map value, or -1 when the tile has no floor. */
        @JvmStatic
        fun definitionId(encoded: Int): Int =
            if (encoded > 0) encoded - 1 else -1

        /** Returns the encoded map value for a definition id, or 0 for "no floor". */
        @JvmStatic
        fun encode(definitionId: Int): Int =
            if (definitionId >= 0) definitionId + 1 else 0
    }
}
