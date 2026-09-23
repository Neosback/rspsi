package com.rspsi.api;

import com.rspsi.api.coords.LocalPoint;
import com.rspsi.api.coords.WorldPoint;

import java.util.List;

/**
 * One scene tile, as {@code net.runelite.api.Tile}.
 *
 * <p>Plane semantics follow the client after {@code Scene.setLinkBelow}: a
 * bridge column is shifted down one scene plane, {@link #getPlane()} is the
 * shifted scene plane, {@link #getRenderLevel()} is the level whose heights the
 * tile uses, and {@link #getBridge()} is the demoted ground tile underneath.
 * {@link #getAuthoredPlane()} is the Studio extra naming the map-file plane.</p>
 *
 * <p>Getters return {@code null} for absent layers, matching RuneLite.</p>
 */
public interface Tile {
    WorldPoint getWorldLocation();

    LocalPoint getLocalLocation();

    Point getSceneLocation();

    /** Scene plane after bridge shifting. */
    int getPlane();

    /** Level the tile's heights come from. */
    int getRenderLevel();

    /** Minimum level used by scene traversal and plane culling. */
    int getPhysicalLevel();

    /** Studio extra: plane the tile is authored on in the map file. */
    int getAuthoredPlane();

    /** Studio extra: authored tile settings flags. */
    int getTileSettings();

    /** The ground tile under this bridge tile, or {@code null}. */
    Tile getBridge();

    SceneTilePaint getSceneTilePaint();

    SceneTileModel getSceneTileModel();

    WallObject getWallObject();

    DecorativeObject getDecorativeObject();

    GroundObject getGroundObject();

    /** Game objects anchored on this tile. RuneLite returns a fixed array with nulls; this list is compact. */
    List<GameObject> getGameObjects();
}
