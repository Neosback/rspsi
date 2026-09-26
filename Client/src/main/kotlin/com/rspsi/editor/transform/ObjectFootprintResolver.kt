package com.rspsi.editor.transform

import com.rspsi.editor.assets.AssetRepository
import com.rspsi.editor.model.WorldObject
import java.util.Optional

/**
 * Supplies the unrotated definition footprint needed to transform location anchors correctly.
 *
 * This stays a Java/Kotlin SAM contract so existing Java lambdas remain source-compatible while
 * the implementation boundary moves to Kotlin. Definition lookup remains lazy and backend-neutral.
 */
@java.lang.FunctionalInterface
fun interface ObjectFootprintResolver {
    fun resolve(objectPlacement: WorldObject?): Optional<ObjectFootprint>?

    /**
     * Immutable unrotated object dimensions.
     *
     * Kept as a JVM record to preserve the nested Java type
     * `ObjectFootprintResolver.ObjectFootprint` and its record accessors.
     */
    @JvmRecord
    data class ObjectFootprint(
        val width: Int,
        val length: Int,
    ) {
        init {
            if (width <= 0 || length <= 0) {
                throw IllegalArgumentException("Object footprint must be positive")
            }
        }

        override fun toString(): String =
            "ObjectFootprint[width=$width, length=$length]"
    }

    companion object {
        /**
         * Resolves footprints directly from the neutral asset facade.
         *
         * The explicit null check preserves the historical Java failure contract.
         */
        @JvmStatic
        fun fromAssets(assets: AssetRepository?): ObjectFootprintResolver {
            val safeAssets = assets ?: throw NullPointerException("assets")
            return ObjectFootprintResolver { objectPlacement ->
                val objectId = objectPlacement!!.id()
                safeAssets.`object`(objectId)
                    .map { definition ->
                        ObjectFootprint(definition.width(), definition.length())
                    }
            }
        }
    }
}
