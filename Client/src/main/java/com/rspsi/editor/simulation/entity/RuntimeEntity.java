package com.rspsi.editor.simulation.entity;

import com.rspsi.editor.model.TileCoordinate;

import java.util.Map;

/**
 * Universal contract for dynamic entities within the runtime simulation and presentation scene.
 */
public interface RuntimeEntity {

    enum EntityType {
        NPC,
        PLAYER,
        PROJECTILE,
        SPOT_ANIMATION,
        GROUND_ITEM,
        TEMPORARY_OBJECT
    }

    long id();

    EntityType type();

    int plane();

    double worldX();

    double worldY();

    /** Returns the integer tile coordinate occupied by this entity's center or root. */
    default TileCoordinate tileCoordinate() {
        return new TileCoordinate(plane(), (int) Math.floor(worldX()), (int) Math.floor(worldY()));
    }

    /** Orientation in standard OSRS 2048-unit circle (0=South, 512=West, 1024=North, 1536=East). */
    int orientation();

    int animationSequence();

    int animationFrame();

    String serverState();

    Map<String, Object> metadata();

    /** Returns an updated entity instance with moved coordinates. */
    RuntimeEntity withPosition(double worldX, double worldY);

    /** Returns an updated entity instance with new animation sequence and frame. */
    RuntimeEntity withAnimation(int sequenceId, int frameIndex);

    /** Returns an updated entity instance with new orientation. */
    RuntimeEntity withOrientation(int orientation);
}
