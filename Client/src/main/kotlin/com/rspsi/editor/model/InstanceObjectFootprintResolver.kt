package com.rspsi.editor.model

/**
 * Resolves an unrotated OSRS object footprint for instance materialization.
 *
 * This remains a SAM contract so Java/Kotlin lambda call sites stay source-compatible. The
 * resolver is deliberately definition-agnostic; callers may source dimensions from any cache
 * adapter without leaking that dependency into the neutral model.
 */
@java.lang.FunctionalInterface
fun interface InstanceObjectFootprintResolver {
    fun resolve(objectPlacement: WorldObject?): Footprint?

    /**
     * Immutable unrotated footprint dimensions for one object definition.
     *
     * Kept as a JVM record so Java callers retain record accessors/reflection and the historical
     * nested type name `InstanceObjectFootprintResolver.Footprint`.
     */
    @JvmRecord
    data class Footprint(
        val width: Int,
        val length: Int,
    ) {
        init {
            if (width <= 0 || length <= 0) {
                throw IllegalArgumentException("Object footprint must be positive")
            }
        }

        override fun toString(): String =
            "Footprint[width=$width, length=$length]"
    }

    companion object {
        /** Returns the compatibility resolver that treats every object as one tile. */
        @JvmStatic
        fun unit(): InstanceObjectFootprintResolver =
            InstanceObjectFootprintResolver { Footprint(1, 1) }

        /** Preserves the historical explicit null-check helper used by instance materialization. */
        @JvmStatic
        fun requireNonNull(
            resolver: InstanceObjectFootprintResolver?,
        ): InstanceObjectFootprintResolver =
            resolver ?: throw NullPointerException("resolver")
    }
}
