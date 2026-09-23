package com.rspsi.editor.render;

import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldRegionWindow;
import com.rspsi.editor.model.WorldTileAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GpuModelColorParityTest {
    @Test
    void smoothModelFacePreservesThreeLitColorsAtGpuBoundary() {
        GpuUploadPlan plan = planFor(new ModelTriangle(
                0, 1, 2, 10, 20, 30,
                -1, 0, 0, 0,
                0, 0, 1, 0, 0, 1,
                100, 0));

        assertEquals(List.of(10, 20, 30),
                plan.vertices().stream().map(GpuSceneVertex::encodedColor).toList());
        assertEquals(List.of(GpuColorEncoding.PACKED_JAGEX_HSL,
                        GpuColorEncoding.PACKED_JAGEX_HSL,
                        GpuColorEncoding.PACKED_JAGEX_HSL),
                plan.vertices().stream().map(GpuSceneVertex::colorEncoding).toList());
    }

    @Test
    void flatClientSentinelExpandsColorAWithoutChangingModelSlots() {
        ModelTriangle face = new ModelTriangle(
                0, 1, 2, 42, 0, ModelFaceColorContract.FLAT_SENTINEL,
                -1, 0, 0, 1,
                0, 0, 1, 0, 0, 1,
                100, 0);

        GpuUploadPlan plan = planFor(face);

        assertEquals(0, face.colorB(),
                "client Model.faceColors2 stays zero for flat faces");
        assertEquals(ModelFaceColorContract.FLAT_SENTINEL, face.colorC());
        assertEquals(List.of(42, 42, 42),
                plan.vertices().stream().map(GpuSceneVertex::encodedColor).toList(),
                "GPU upload expands the flat face only at the backend boundary");
    }

    private static GpuUploadPlan planFor(ModelTriangle face) {
        TileCoordinate coordinate = new TileCoordinate(0, 3200, 3200);
        WorldTileAddress address = WorldTileAddress.of(3200, 3200, 0);
        ModelRenderPacket model = new ModelRenderPacket(
                coordinate, 42, ObjectCategory.GROUND,
                List.of(
                        new ModelVertex(0, 0, 0, 0, -256, 0, 1, 0, 0),
                        new ModelVertex(64, 0, 0, 0, -256, 0, 1, 1, 0),
                        new ModelVertex(0, 0, 64, 0, -256, 0, 1, 0, 1)),
                List.of(face), List.of(), -1,
                0, 0, 0, 64, 0, 64, false, false);
        SceneTileSnapshot tile = new SceneTileSnapshot(
                coordinate, address, 0, 0,
                Optional.empty(), Optional.empty(), List.of(model),
                List.of(new SceneLayer(SceneLayer.Kind.GROUND_OBJECT, List.of(0))),
                List.of(), false, false);
        GpuScenePacket packet = new GpuScenePacket(
                new SceneWindow(new WorldRegionWindow(50, 50, 1, 1, Map.of()),
                        3200, 3200, 1, 0, Set.of(), List.of()),
                List.of(tile), LightingProfile.osrs(), "model-color-parity", Map.of());
        return new GpuUploadPlanBuilder().build(packet);
    }
}
