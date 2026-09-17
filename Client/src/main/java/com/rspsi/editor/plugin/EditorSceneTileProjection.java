package com.rspsi.editor.plugin;

import com.rspsi.editor.collision.CollisionTileSnapshot;
import com.rspsi.editor.model.BridgeLink;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.TerrainLight;
import com.rspsi.editor.render.TerrainMaterial;
import com.rspsi.editor.render.TerrainAppearance;
import com.rspsi.editor.terrain.TerrainMesh;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable scene projection for one tile.
 *
 * <p>This is deliberately a projection rather than another scene graph. The
 * authored {@link TileSnapshot} remains the save/edit identity; the optional
 * terrain, collision, bridge, and object values are derived host-owned views
 * that a renderer or plugin may inspect without reaching back into the
 * mutable document.</p>
 */
public record EditorSceneTileProjection(
        TileCoordinate coordinate,
        TileSnapshot authored,
        int effectivePlane,
        Optional<BridgeLink> bridge,
        Optional<TerrainMesh> terrain,
        Optional<TerrainMaterial> material,
        Optional<TerrainAppearance> appearance,
        Optional<TerrainLight> lighting,
        Optional<CollisionTileSnapshot> collision,
        List<RenderObject> objects
) {
    public EditorSceneTileProjection {
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        authored = Objects.requireNonNull(authored, "authored");
        bridge = Objects.requireNonNull(bridge, "bridge");
        terrain = Objects.requireNonNull(terrain, "terrain");
        material = Objects.requireNonNull(material, "material");
        appearance = Objects.requireNonNull(appearance, "appearance");
        lighting = Objects.requireNonNull(lighting, "lighting");
        collision = Objects.requireNonNull(collision, "collision");
        objects = List.copyOf(Objects.requireNonNull(objects, "objects"));
        if (effectivePlane < -1) {
            throw new IllegalArgumentException("Effective plane must be -1 or greater");
        }
        for (RenderObject object : objects) {
            Objects.requireNonNull(object, "objects cannot contain null values");
        }
    }

    /** Returns the renderer layer order used for overlapping object projections. */
    public List<RenderObject> objectsBySceneLayer() {
        return objects;
    }

    /** True when this authored tile owns a bridge relationship. */
    public boolean hasBridge() {
        return bridge.isPresent();
    }

    /** True when this authored tile maps to a visible/collidable scene plane. */
    public boolean hasEffectiveSurface() {
        return effectivePlane >= 0;
    }

    /** True when the projection contains at least one known object category. */
    public boolean hasKnownObjects() {
        return objects.stream().anyMatch(object -> object.category() != ObjectCategory.UNKNOWN);
    }
}
