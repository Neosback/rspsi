package com.rspsi.editor.model

import java.util.Optional

/**
 * Coordinate adapter for one open [WorldDocument] placed inside an absolute [WorldWindow].
 *
 * This class deliberately composes the two existing owners instead of duplicating coordinate
 * math. [WorldDocument] decides whether a local tile exists; [WorldWindow] owns local/world
 * translation. Keeping that split here prevents tools and callers from reinterpreting matching
 * numeric coordinates across the two spaces.
 */
class DocumentCoordinates(
    document: WorldDocument?,
    window: WorldWindow?,
) {
    private val document: WorldDocument =
        document ?: throw NullPointerException("document")
    private val window: WorldWindow =
        window ?: throw NullPointerException("window")

    init {
        if (this.document.width() != this.window.width ||
            this.document.length() != this.window.length
        ) {
            throw IllegalArgumentException(
                "Document/window dimensions differ: " +
                    this.document.width() + "x" + this.document.length() + " vs " +
                    this.window.width + "x" + this.window.length,
            )
        }
    }

    /** Java-compatible accessor retained while callers migrate incrementally. */
    fun document(): WorldDocument = document

    /** Java-compatible accessor retained while callers migrate incrementally. */
    fun window(): WorldWindow = window

    /**
     * Converts an absolute tile when both the world window and document bounds accept it.
     *
     * Null and planes above the document remain a non-exceptional miss for compatibility with
     * the previous Java API.
     */
    fun toLocal(worldTile: WorldTile?): Optional<LocalTile> {
        if (worldTile == null || worldTile.plane >= document.planes()) {
            return Optional.empty()
        }
        return window.tryToLocal(worldTile)
            .filter { document.contains(it) }
    }

    fun requireLocal(worldTile: WorldTile?): LocalTile =
        toLocal(worldTile).orElseThrow {
            IndexOutOfBoundsException("World tile outside document window: $worldTile")
        }

    fun toWorld(localTile: LocalTile?): WorldTile {
        val safeLocalTile = localTile ?: throw NullPointerException("localTile")
        if (!document.contains(safeLocalTile)) {
            throw IndexOutOfBoundsException("Local tile outside document: $safeLocalTile")
        }
        return window.toWorld(safeLocalTile)
    }
}
