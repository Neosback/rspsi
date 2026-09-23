package com.rspsi.api;

import com.rspsi.api.coords.LocalPoint;
import com.rspsi.api.coords.WorldPoint;

/**
 * Any placed loc in a scene layer, as {@code net.runelite.api.TileObject}.
 *
 * <p>Identity is the <em>placed</em> loc: {@link #getId()} is the id written
 * in the map, even when a multiloc transform supplies the visible model.</p>
 */
public interface TileObject {
    /**
     * Stable identity hash for this placement. Unlike RuneLite this is not the
     * client's packed scene tag; it is derived from {@link #getStableId()} and
     * survives scene rebuilds.
     */
    long getHash();

    /** Studio extra: human-readable stable placement identity. */
    String getStableId();

    /** Placed object (loc) id. */
    int getId();

    /** Loc shape/type (0-22). */
    int getType();

    /** Authored rotation (0-3). */
    int getRotation();

    /** World tile of the object's anchor, on the scene plane (RuneLite semantics). */
    WorldPoint getWorldLocation();

    /** Local position of the anchor tile's centre. */
    LocalPoint getLocalLocation();

    /** Scene plane. */
    int getPlane();

    /** Studio extra: plane the loc is authored on in the map file. */
    int getAuthoredPlane();

    /**
     * Studio extra: whether the scene submitted model geometry for this loc.
     * Invisible blockers, authored-empty models and unresolved multilocs are
     * still scene objects (as in the client) but render nothing.
     */
    boolean isRendered();

    default int getX() {
        return getLocalLocation().getX();
    }

    default int getY() {
        return getLocalLocation().getY();
    }

    /**
     * Client config bitfield: {@code type & 31 | rotation << 6}. The
     * supports-items bit is not modelled yet.
     */
    default int getConfig() {
        return (getType() & 31) | (getRotation() << 6);
    }
}
