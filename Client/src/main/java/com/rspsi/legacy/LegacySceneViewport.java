package com.rspsi.legacy;

import com.jagex.map.SceneGraph;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.viewport.Viewport;

import java.util.Optional;

/**
 * Bridges the existing SceneGraph hover calculation to the neutral viewport.
 * The x/y arguments are retained for the future renderer picker; SceneGraph's
 * current render loop remains the source of hover truth during this migration.
 */
public final class LegacySceneViewport implements Viewport {
    private final SceneGraph sceneGraph;

    public LegacySceneViewport(SceneGraph sceneGraph) {
        this.sceneGraph = java.util.Objects.requireNonNull(sceneGraph, "sceneGraph");
    }

    @Override
    public Optional<WorldTile> tileAt(float x, float y) {
        int plane = SceneGraph.hoveredTileZ;
        int tileX = SceneGraph.hoveredTileX;
        int tileY = SceneGraph.hoveredTileY;
        if (plane < 0 || plane >= sceneGraph.tiles.length || tileX < 0
                || tileX >= sceneGraph.width || tileY < 0 || tileY >= sceneGraph.length) {
            return Optional.empty();
        }
        return Optional.of(new WorldTile(plane, tileX, tileY));
    }
}
