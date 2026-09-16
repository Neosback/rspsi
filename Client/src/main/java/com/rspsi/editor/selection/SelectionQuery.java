package com.rspsi.editor.selection;

import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.ObjectCategory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Neutral attribute queries used by selection tools and future inspectors. */
public final class SelectionQuery {
    private SelectionQuery() { }

    public static Set<WorldObject> objects(WorldDocument world, ObjectFilter filter) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(filter, "filter");
        Set<WorldObject> result = new LinkedHashSet<>();
        for (int plane = 0; plane < world.planes(); plane++) {
            for (int x = 0; x < world.width(); x++) {
                for (int y = 0; y < world.length(); y++) {
                    for (WorldObject object : world.tile(plane, x, y).snapshot().objects()) {
                        if (filter.matches(object)) result.add(object);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    public static Set<TileCoordinate> tiles(WorldDocument world, TileFilter filter) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(filter, "filter");
        Set<TileCoordinate> result = new LinkedHashSet<>();
        for (int plane = 0; plane < world.planes(); plane++) {
            for (int x = 0; x < world.width(); x++) {
                for (int y = 0; y < world.length(); y++) {
                    TileSnapshot tile = world.tile(plane, x, y).snapshot();
                    if (filter.matches(plane, tile)) result.add(new TileCoordinate(plane, x, y));
                }
            }
        }
        return Set.copyOf(result);
    }

    public record ObjectFilter(Integer id, Integer type, Integer plane, Integer rotation, ObjectCategory category) {
        public ObjectFilter(Integer id, Integer type, Integer plane, Integer rotation) {
            this(id, type, plane, rotation, null);
        }

        public ObjectFilter {
            if (id != null && id < 0) throw new IllegalArgumentException("Object ID cannot be negative");
            if (type != null && (type < 0 || type > 63)) throw new IllegalArgumentException("Object type must be 0..63");
            if (plane != null && plane < 0) throw new IllegalArgumentException("Object plane cannot be negative");
            if (rotation != null && (rotation < 0 || rotation > 3)) throw new IllegalArgumentException("Object rotation must be 0..3");
        }

        public boolean matches(WorldObject object) {
            return (id == null || id == object.id())
                    && (type == null || type == object.type())
                    && (plane == null || plane == object.plane())
                    && (rotation == null || rotation == object.rotation())
                    && (category == null || category == object.category());
        }
    }

    public record TileFilter(Integer plane, Integer underlayId, Integer overlayId, Integer requiredFlagsMask) {
        public TileFilter {
            if (plane != null && plane < 0) throw new IllegalArgumentException("Tile plane cannot be negative");
            if (underlayId != null && (underlayId < 0 || underlayId > 255)) {
                throw new IllegalArgumentException("Underlay ID must be 0..255");
            }
            if (overlayId != null && (overlayId < 0 || overlayId > 65535)) {
                throw new IllegalArgumentException("Overlay ID must be 0..65535");
            }
            if (requiredFlagsMask != null && requiredFlagsMask < 0) {
                throw new IllegalArgumentException("Required flags mask cannot be negative");
            }
        }

        public boolean matches(int tilePlane, TileSnapshot tile) {
            return (plane == null || plane == tilePlane)
                    && (underlayId == null || underlayId == tile.underlayId())
                    && (overlayId == null || overlayId == tile.overlayId())
                    && (requiredFlagsMask == null || (tile.flags() & requiredFlagsMask) == requiredFlagsMask);
        }
    }
}
