package com.rspsi.editor.selection;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;

import java.util.Set;

/**
 * JVM compatibility shell for neutral attribute queries used by selection tools and inspectors.
 *
 * <p>Traversal, filter validation, and matching semantics live in
 * {@link SelectionQuerySemantics}. Nested records remain here to preserve their Java ABI.</p>
 */
public final class SelectionQuery {
    private SelectionQuery() {
    }

    public static Set<WorldObject> objects(WorldDocument world, ObjectFilter filter) {
        return SelectionQuerySemantics.objects(world, filter);
    }

    public static Set<TileCoordinate> tiles(WorldDocument world, TileFilter filter) {
        return SelectionQuerySemantics.tiles(world, filter);
    }

    public record ObjectFilter(
            Integer id,
            Integer type,
            Integer plane,
            Integer rotation,
            ObjectCategory category
    ) {
        public ObjectFilter(Integer id, Integer type, Integer plane, Integer rotation) {
            this(id, type, plane, rotation, null);
        }

        public ObjectFilter {
            SelectionQuerySemantics.validateObjectFilter(id, type, plane, rotation);
        }

        public boolean matches(WorldObject object) {
            return SelectionQuerySemantics.matchesObject(this, object);
        }
    }

    public record TileFilter(
            Integer plane,
            Integer underlayId,
            Integer overlayId,
            Integer requiredFlagsMask
    ) {
        public TileFilter {
            SelectionQuerySemantics.validateTileFilter(
                    plane,
                    underlayId,
                    overlayId,
                    requiredFlagsMask);
        }

        public boolean matches(int tilePlane, TileSnapshot tile) {
            return SelectionQuerySemantics.matchesTile(this, tilePlane, tile);
        }
    }
}
