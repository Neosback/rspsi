package com.rspsi.editor.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneLayerTest {
    @Test
    void preservesCategoryOrderAndSeparatesTransparentModels() {
        SceneLayer layer = new SceneLayer(SceneLayer.Kind.WALL,
                List.of(2, 5, 9), List.of(2, 9), List.of(5));

        assertEquals(List.of(2, 5, 9), layer.modelIndices());
        assertEquals(List.of(2, 9), layer.opaqueModelIndices());
        assertEquals(List.of(5), layer.transparentModelIndices());
    }

    @Test
    void acceptsOpaqueOnlyLayers() {
        SceneLayer layer = new SceneLayer(SceneLayer.Kind.WALL,
                List.of(4), List.of(4), List.of());

        assertEquals(List.of(4), layer.opaqueModelIndices());
        assertEquals(List.of(), layer.transparentModelIndices());
    }

    @Test
    void modelPacketsExposeFaceLevelAlphaPartitions() {
        ModelRenderPacket packet = new ModelRenderPacket(
                new com.rspsi.editor.model.TileCoordinate(0, 3200, 3200), 1,
                com.rspsi.editor.model.ObjectCategory.GROUND,
                List.of(new ModelVertex(0, 0, 0, 0, 1, 0, 0, 0),
                        new ModelVertex(128, 0, 0, 0, 1, 0, 0, 0),
                        new ModelVertex(0, 128, 0, 0, 1, 0, 0, 0)),
                List.of(new ModelTriangle(0, 1, 2, 1, 1, 1, -1, 0, 0, 0),
                        new ModelTriangle(0, 2, 1, 1, 1, 1, -1, 128, 0, 0)),
                List.of(), -1, 0, 0, 0, 128, 128, 0, false, false);

        assertEquals(List.of(0), packet.opaqueTriangleIndices());
        assertEquals(List.of(1), packet.transparentTriangleIndices());
    }
}
