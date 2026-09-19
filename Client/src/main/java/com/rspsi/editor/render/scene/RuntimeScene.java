package com.rspsi.editor.render.scene;

import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.simulation.entity.RuntimeEntity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the dynamic runtime presentation layer:
 * active NPCs, players, spot animations, ground items, and temporary animated objects.
 */
public record RuntimeScene(
        long clientCycle,
        long serverTick,
        List<RuntimeEntity> entities,
        List<ModelRenderPacket> dynamicModelPackets
) {
    public static final RuntimeScene EMPTY = new RuntimeScene(0L, 0L, List.of(), List.of());

    public RuntimeScene {
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(dynamicModelPackets, "dynamicModelPackets");
        entities = Collections.unmodifiableList(List.copyOf(entities));
        dynamicModelPackets = Collections.unmodifiableList(List.copyOf(dynamicModelPackets));
    }

    public static RuntimeScene empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entities.isEmpty() && dynamicModelPackets.isEmpty();
    }
}
