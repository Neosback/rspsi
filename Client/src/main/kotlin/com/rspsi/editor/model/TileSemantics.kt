package com.rspsi.editor.model

import java.util.ArrayList

/**
 * Canonical compatibility semantics for [Tile].
 *
 * [Tile] remains a minimal Java shell because its constructor is package-private, a JVM visibility
 * level Kotlin cannot represent exactly. Keeping that shell prevents callers outside the model
 * package from constructing canonical tiles while Kotlin owns initialization, provenance
 * resolution, restore helpers, and defensive object-list copying.
 */
object TileSemantics {
    @JvmStatic
    fun initialHeightSource(): TerrainHeightSource =
        TerrainHeightSource.unknown()

    @JvmStatic
    fun initialSnapshot(source: TerrainHeightSource): TileSnapshot =
        TileSnapshot(
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            java.util.List.of(),
            source,
        )

    @JvmStatic
    fun requireState(state: TileSnapshot?): TileSnapshot =
        state ?: throw NullPointerException("state")

    @JvmStatic
    fun requireHeightSource(source: TerrainHeightSource?): TerrainHeightSource =
        source ?: throw NullPointerException("heightSource")

    @JvmStatic
    fun requireRestoreSource(source: TerrainHeightSource?): TerrainHeightSource =
        source ?: throw NullPointerException("source")

    /**
     * A known provenance carried by the incoming snapshot wins. Legacy/unknown snapshots inherit
     * the tile's current provenance so ordinary authored-value restores do not erase cache replay
     * metadata.
     */
    @JvmStatic
    fun resolveRestoreHeightSource(
        current: TerrainHeightSource,
        state: TileSnapshot,
    ): TerrainHeightSource {
        val incoming = state.heightSource()
        return if (incoming.known()) incoming else current
    }

    @JvmStatic
    fun withHeightSource(
        state: TileSnapshot,
        source: TerrainHeightSource,
    ): TileSnapshot =
        state.withHeightSource(source)

    /** Returns the historical mutable defensive copy of the tile's location list. */
    @JvmStatic
    fun copyObjects(state: TileSnapshot): List<WorldObject> =
        ArrayList(state.objects())
}
