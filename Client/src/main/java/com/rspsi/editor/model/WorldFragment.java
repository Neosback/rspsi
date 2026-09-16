package com.rspsi.editor.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Portable copy/paste payload for terrain and locations. Coordinates remain
 * world coordinates in the captured fragment; paste operations translate them
 * relative to {@link #bounds()}.
 */
public record WorldFragment(
        TileBounds bounds,
        List<TerrainTilePatch> terrain,
        List<WorldObject> objects
) {
    public WorldFragment {
        bounds = Objects.requireNonNull(bounds, "bounds");
        terrain = List.copyOf(terrain == null ? List.of() : terrain);
        objects = List.copyOf(objects == null ? List.of() : objects);
        for (TerrainTilePatch patch : terrain) {
            if (!bounds.contains(patch.x(), patch.y())) {
                throw new IllegalArgumentException("Terrain patch is outside fragment bounds");
            }
        }
        for (WorldObject object : objects) {
            if (!bounds.contains(object.x(), object.y())) {
                throw new IllegalArgumentException("Object is outside fragment bounds");
            }
        }
    }

    /** Captures every plane in the selected rectangle from a canonical document. */
    public static WorldFragment capture(WorldDocument document, TileBounds bounds) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(bounds, "bounds");
        if (bounds.maxX() >= document.width() || bounds.maxY() >= document.length()) {
            throw new IllegalArgumentException("Fragment bounds exceed document dimensions");
        }

        List<TerrainTilePatch> patches = new ArrayList<>();
        LinkedHashSet<WorldObject> objects = new LinkedHashSet<>();
        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    TileSnapshot source = document.tile(plane, x, y).snapshot();
                    objects.addAll(source.objects());
                    patches.add(new TerrainTilePatch(plane, x, y, withoutObjects(source)));
                }
            }
        }
        return new WorldFragment(bounds, patches, new ArrayList<>(objects));
    }

    private static TileSnapshot withoutObjects(TileSnapshot source) {
        return new TileSnapshot(source.southWestHeight(), source.southEastHeight(),
                source.northEastHeight(), source.northWestHeight(), source.underlayId(),
                source.overlayId(), source.overlayShape(), source.overlayRotation(),
                source.flags(), List.of());
    }
}
