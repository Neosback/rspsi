package com.rspsi.editor.model

/**
 * Derived-data invalidation state for one 8x8 editor chunk.
 *
 * [plane] is normally the authored document plane. The compatibility value -1 means the same
 * chunk is invalid across every document plane. The boolean fields identify which derived
 * products need rebuilding; authored world state is not stored here.
 */
@JvmRecord
data class DirtyRegion(
    val plane: Int,
    val chunkX: Int,
    val chunkY: Int,
    val terrain: Boolean,
    val objects: Boolean,
    val collision: Boolean,
    val minimap: Boolean,
    val render: Boolean,
) {
    init {
        if (plane < -1 || chunkX < 0 || chunkY < 0) {
            throw IllegalArgumentException(
                "Plane must be -1 or non-negative and chunks cannot be negative",
            )
        }
    }

    /**
     * Compatibility constructor for callers that invalidate the same chunk across every
     * document plane. Canonical session invalidations use the plane-aware form.
     */
    constructor(
        chunkX: Int,
        chunkY: Int,
        terrain: Boolean,
        objects: Boolean,
        collision: Boolean,
        minimap: Boolean,
        render: Boolean,
    ) : this(-1, chunkX, chunkY, terrain, objects, collision, minimap, render)

    /**
     * Coalesces invalidation reasons for exactly the same plane/chunk identity.
     *
     * Merging is intentionally an OR operation so no already-dirty derived product can become
     * clean while edits are being batched.
     */
    fun merge(other: DirtyRegion?): DirtyRegion {
        val safeOther = other ?: throw NullPointerException("other")
        if (plane != safeOther.plane || chunkX != safeOther.chunkX || chunkY != safeOther.chunkY) {
            throw IllegalArgumentException("Can only merge the same plane and dirty chunk")
        }
        return DirtyRegion(
            plane,
            chunkX,
            chunkY,
            terrain || safeOther.terrain,
            objects || safeOther.objects,
            collision || safeOther.collision,
            minimap || safeOther.minimap,
            render || safeOther.render,
        )
    }

    override fun toString(): String =
        "DirtyRegion[plane=$plane, chunkX=$chunkX, chunkY=$chunkY, terrain=$terrain, " +
            "objects=$objects, collision=$collision, minimap=$minimap, render=$render]"

    companion object {
        /**
         * Maps the legacy local coordinate to its owning 8x8 dirty chunk.
         *
         * TileCoordinate remains here only because command/history/selection callers have not
         * completed their LocalTile migration yet.
         */
        @JvmStatic
        fun forTile(coordinate: TileCoordinate?): DirtyRegion {
            val safeCoordinate = coordinate ?: throw NullPointerException("coordinate")
            return DirtyRegion(
                safeCoordinate.plane(),
                safeCoordinate.x() / 8,
                safeCoordinate.y() / 8,
                true,
                true,
                true,
                true,
                true,
            )
        }
    }
}
