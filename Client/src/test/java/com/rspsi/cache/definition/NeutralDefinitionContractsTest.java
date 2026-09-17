package com.rspsi.cache.definition;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NeutralDefinitionContractsTest {

    @Test
    void optionalModelAndTextureCapabilitiesDoNotBreakLegacyProviders() {
        DefinitionProvider provider = new DefinitionProvider() {
            @Override public Optional<ObjectDefinitionView> object(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> underlay(int id) { return Optional.empty(); }
            @Override public Optional<FloorDefinitionView> overlay(int id) { return Optional.empty(); }
        };

        assertTrue(provider.model(1).isEmpty());
        assertTrue(provider.texture(1).isEmpty());
        assertTrue(provider.objectCollision(1).isEmpty());
    }

    @Test
    void textureAndModelViewsValidateStableMetadata() {
        TextureDefinitionView texture = new TextureDefinitionView(4, false, 12, 0x123456, 1, 2, false);
        ModelDefinitionView model = new ModelDefinitionView(8, 10, 12, 2, 3);

        assertEquals(12, texture.fileId());
        assertEquals(10, model.vertexCount());
        assertThrows(IllegalArgumentException.class,
                () -> new ModelDefinitionView(8, -1, 12, 2, 3));
    }

    @Test
    void modelGeometryViewOwnsArraysAndValidatesFaceIndices() {
        int[] vertices = {0, 1, 2, 10, 11, 12, 20, 21, 22};
        int[] triangles = {0, 1, 2};
        ModelGeometryView view = new ModelGeometryView(9, vertices, triangles,
                new short[]{123}, new int[]{255}, new int[]{-1});

        vertices[0] = 999;
        triangles[0] = 2;
        assertEquals(0, view.vertexPositions()[0]);
        assertEquals(0, view.triangleIndices()[0]);
        assertEquals(3, view.vertexCount());
        assertEquals(1, view.triangleCount());

        assertThrows(IllegalArgumentException.class,
                () -> new ModelGeometryView(9, new int[]{0, 0, 0}, new int[]{0, 1, 2},
                        null, null, null));
    }
}
