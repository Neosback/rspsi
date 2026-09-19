package com.rspsi.editor.simulation.entity;

import com.rspsi.editor.model.TileCoordinate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Spatial registry and lifecycle container for active runtime entities in the simulation scene.
 */
public final class EntityWorld {
    private final Map<Long, RuntimeEntity> entities = new ConcurrentHashMap<>();

    public void add(RuntimeEntity entity) {
        Objects.requireNonNull(entity, "entity");
        entities.put(entity.id(), entity);
    }

    public Optional<RuntimeEntity> remove(long entityId) {
        return Optional.ofNullable(entities.remove(entityId));
    }

    public Optional<RuntimeEntity> get(long entityId) {
        return Optional.ofNullable(entities.get(entityId));
    }

    public Collection<RuntimeEntity> all() {
        return Collections.unmodifiableCollection(entities.values());
    }

    public int size() {
        return entities.size();
    }

    public void clear() {
        entities.clear();
    }

    public List<RuntimeEntity> at(int plane, int x, int y) {
        TileCoordinate target = new TileCoordinate(plane, x, y);
        List<RuntimeEntity> result = new ArrayList<>();
        for (RuntimeEntity e : entities.values()) {
            if (e.tileCoordinate().equals(target)) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<RuntimeEntity> inArea(int plane, int minX, int minY, int maxX, int maxY) {
        List<RuntimeEntity> result = new ArrayList<>();
        for (RuntimeEntity e : entities.values()) {
            if (e.plane() == plane && e.worldX() >= minX && e.worldX() <= maxX
                    && e.worldY() >= minY && e.worldY() <= maxY) {
                result.add(e);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<RuntimeEntity> ofType(RuntimeEntity.EntityType type) {
        Objects.requireNonNull(type, "type");
        return entities.values().stream()
                .filter(e -> e.type() == type)
                .collect(Collectors.toUnmodifiableList());
    }
}
