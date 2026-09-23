package com.rspsi.editor.model;

/**
 * The one conversion between floor ids as stored in map data and floor
 * definition ids.
 *
 * <p>Map tiles (and {@link TileSnapshot#underlayId()}/{@link TileSnapshot#overlayId()})
 * store {@code definitionId + 1}, with 0 meaning "no floor"; the client
 * resolves them with {@code getOverlay(value - 1)}
 * ({@code runescape-client/class470}, the scene tile builder). Tools and
 * painter state keep the encoded value so it round-trips with tiles; UI
 * labels and definition lookups use {@link #definitionId(int)}.</p>
 */
public final class FloorId {
    private FloorId() {
    }

    /** Definition id for an encoded map value, or -1 when the tile has no floor. */
    public static int definitionId(int encoded) {
        return encoded > 0 ? encoded - 1 : -1;
    }

    /** Encoded map value for a definition id, or 0 for "no floor". */
    public static int encode(int definitionId) {
        return definitionId >= 0 ? definitionId + 1 : 0;
    }
}
