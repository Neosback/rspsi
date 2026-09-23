package com.rspsi.api.scene;

import com.rspsi.api.SceneTilePaint;
import com.rspsi.editor.render.TerrainRenderFace;
import com.rspsi.editor.render.TerrainRenderPacket;
import com.rspsi.editor.render.TerrainRenderVertex;

/**
 * {@link SceneTilePaint} for scene shape 0 (underlay only) or 1 (full overlay),
 * following {@code runescape-client/Scene.addTile}: shape 0 paints the lit
 * underlay corners with no texture and is never flat; shape 1 paints the lit
 * overlay corners with the overlay texture and is flat when its heights match.
 */
record ResolvedTilePaint(int getSwColor, int getSeColor, int getNeColor, int getNwColor,
                         int getTexture, boolean isFlat, int getMinimapHsl) implements SceneTilePaint {

    static SceneTilePaint of(TerrainRenderPacket terrain, boolean heightsFlat) {
        boolean overlay = terrain.shape() == 1;
        int material = overlay ? 1 : 0;
        int sw = cornerHsl(terrain, material, 0, 0);
        int se = cornerHsl(terrain, material, 128, 0);
        int ne = cornerHsl(terrain, material, 128, 128);
        int nw = cornerHsl(terrain, material, 0, 128);
        if (sw == Integer.MIN_VALUE || se == Integer.MIN_VALUE
                || ne == Integer.MIN_VALUE || nw == Integer.MIN_VALUE) {
            // A hidden overlay or missing underlay leaves no drawn corners.
            return null;
        }
        return new ResolvedTilePaint(sw, se, ne, nw,
                overlay ? terrain.textureId() : -1,
                overlay && heightsFlat,
                overlay ? terrain.overlayMinimapHsl() : terrain.underlayHsl());
    }

    private static int cornerHsl(TerrainRenderPacket terrain, int material, int x, int y) {
        for (TerrainRenderFace face : terrain.faces()) {
            if (face.material() != material) continue;
            for (int index : new int[]{face.a(), face.b(), face.c()}) {
                TerrainRenderVertex vertex = terrain.vertices().get(index);
                if (vertex.x() == x && vertex.y() == y) return vertex.packedHsl();
            }
        }
        return Integer.MIN_VALUE;
    }
}
