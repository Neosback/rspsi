package com.rspsi.editor.model

/**
 * Provenance for terrain heights.
 *
 * [cacheEncoded] means the source can be replayed exactly through the cache codec.
 * [authored] identifies a height created by an editor operation whose cache encoding
 * must be derived when the document is saved. Unknown is intentionally distinct from
 * authored so publication logic can distinguish missing provenance from edited terrain.
 */
@JvmRecord
data class TerrainHeightSource(
    val generated: Boolean,
    val explicitValue: Int,
    val cacheEncoded: Boolean,
    val authored: Boolean,
) {
    init {
        if (explicitValue !in 0..255) {
            throw IllegalArgumentException(
                "Terrain height value must be between 0 and 255",
            )
        }
        if (generated && authored) {
            throw IllegalArgumentException(
                "A generated cache height cannot also be editor-authored",
            )
        }
    }

    /** Backward-compatible constructor for cache-decoder call sites. */
    constructor(
        generated: Boolean,
        explicitValue: Int,
        cacheEncoded: Boolean,
    ) : this(generated, explicitValue, cacheEncoded, false)

    fun known(): Boolean = generated || cacheEncoded || authored

    override fun toString(): String =
        "TerrainHeightSource[" +
            "generated=$generated, " +
            "explicitValue=$explicitValue, " +
            "cacheEncoded=$cacheEncoded, " +
            "authored=$authored]"

    companion object {
        @JvmStatic
        fun generatedSource(): TerrainHeightSource =
            TerrainHeightSource(true, 0, true, false)

        /**
         * Cache opcode value 1 represents a generated/default height rather than literal 1.
         * Preserve the decoder's historical normalization by storing it as explicit value 0.
         */
        @JvmStatic
        fun explicitSource(value: Int): TerrainHeightSource =
            TerrainHeightSource(false, if (value == 1) 0 else value, true, false)

        @JvmStatic
        fun authoredSource(): TerrainHeightSource =
            TerrainHeightSource(false, 0, false, true)

        @JvmStatic
        fun unknown(): TerrainHeightSource =
            TerrainHeightSource(false, 0, false, false)
    }
}
