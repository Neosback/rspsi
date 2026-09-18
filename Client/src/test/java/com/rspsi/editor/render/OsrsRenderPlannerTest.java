package com.rspsi.editor.render;

import com.rspsi.editor.model.OsrsTileFlags;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldRegion;
import com.rspsi.editor.model.WorldRegionWindow;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrsRenderPlannerTest {
    @Test
    void plannerAppliesRenderConfigBeforeBackendUpload() {
        WorldDocument document = new WorldDocument(64, 64, 4);
        document.tile(1, 2, 3).restore(new TileSnapshot(0, 0, 0, 0,
                0, 0, 0, 0, OsrsTileFlags.BRIDGE, java.util.List.of()));
        WorldRegion region = new WorldRegion(10, 20, document);
        WorldRegionWindow window = new WorldRegionWindow(10, 20, 1, 1,
                Map.of(region.regionId(), region));
        RenderWindowScene scene = new RenderWindowSceneBuilder().build(window);
        RenderConfig config = new RenderConfigCompiler().compile(
                RenderSettingKeys.registry().defaults()
                        .with(RenderSettingKeys.PLANE_SELECTION,
                                SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE)
                        .with(RenderSettingKeys.ACTIVE_PLANE, 0));

        GpuScenePacket packet = new OsrsRenderPlanner().plan(
                SceneWindow.from(window), scene, config);

        assertEquals(64 * 64 + 1, packet.tiles().size());
        assertTrue(packet.tiles().stream().allMatch(tile -> tile.effectivePlane() == 0));
    }
}
