package com.rspsi.editor.simulation.entity;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete RuntimeEntity representing an active NPC instance.
 */
public record NpcEntity(
        long id,
        int definitionId,
        String symbolicName,
        int plane,
        double worldX,
        double worldY,
        int size,
        int orientation,
        int animationSequence,
        int animationFrame,
        int wanderRadius,
        double spawnX,
        double spawnY,
        String serverState,
        Map<String, Object> metadata
) implements RuntimeEntity {

    public NpcEntity {
        Objects.requireNonNull(symbolicName, "symbolicName");
        Objects.requireNonNull(serverState, "serverState");
        metadata = Collections.unmodifiableMap(Map.copyOf(metadata == null ? Map.of() : metadata));
    }

    public static NpcEntity create(long id, int definitionId, String symbolicName, int plane,
                                  double x, double y, int orientation) {
        return new NpcEntity(id, definitionId, symbolicName, plane, x, y, 1, orientation,
                -1, 0, 0, x, y, "idle", Map.of());
    }

    @Override
    public EntityType type() {
        return EntityType.NPC;
    }

    @Override
    public NpcEntity withPosition(double newX, double newY) {
        return new NpcEntity(id, definitionId, symbolicName, plane, newX, newY, size, orientation,
                animationSequence, animationFrame, wanderRadius, spawnX, spawnY, serverState, metadata);
    }

    @Override
    public NpcEntity withAnimation(int sequenceId, int frameIndex) {
        return new NpcEntity(id, definitionId, symbolicName, plane, worldX, worldY, size, orientation,
                sequenceId, frameIndex, wanderRadius, spawnX, spawnY, serverState, metadata);
    }

    @Override
    public NpcEntity withOrientation(int newOrientation) {
        return new NpcEntity(id, definitionId, symbolicName, plane, worldX, worldY, size, newOrientation,
                animationSequence, animationFrame, wanderRadius, spawnX, spawnY, serverState, metadata);
    }

    public NpcEntity withServerState(String newState) {
        return new NpcEntity(id, definitionId, symbolicName, plane, worldX, worldY, size, orientation,
                animationSequence, animationFrame, wanderRadius, spawnX, spawnY, newState, metadata);
    }
}
