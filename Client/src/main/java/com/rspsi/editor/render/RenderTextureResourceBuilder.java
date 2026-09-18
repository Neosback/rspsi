package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.TextureDefinitionView;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Resolves only the textures referenced by the immutable scene packets. */
final class RenderTextureResourceBuilder {
    private static final int OSRS_TEXTURE_SIZE = 128;

    private RenderTextureResourceBuilder() {
    }

    static Map<Integer, RenderTextureResource> build(DefinitionProvider definitions,
                                                      LightingProfile lighting,
                                                      Collection<TerrainRenderPacket> terrainPackets,
                                                      Collection<ModelRenderPacket> modelPackets) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(lighting, "lighting");
        Set<Integer> ids = new TreeSet<>();
        for (TerrainRenderPacket packet : terrainPackets) {
            if (packet.textureId() >= 0) ids.add(packet.textureId());
            packet.faces().stream().map(TerrainRenderFace::textureId)
                    .filter(id -> id >= 0).forEach(ids::add);
        }
        for (ModelRenderPacket packet : modelPackets) {
            packet.triangles().stream().map(ModelTriangle::textureId)
                    .filter(id -> id >= 0).forEach(ids::add);
        }

        Map<Integer, RenderTextureResource> resources = new LinkedHashMap<>();
        for (int id : ids) {
            Optional<TextureDefinitionView> definition = definitions.texture(id);
            if (definition.isEmpty()) continue;
            Optional<int[]> pixels;
            try {
                pixels = definitions.texturePixels(id, lighting.textureGamma(), OSRS_TEXTURE_SIZE);
            } catch (RuntimeException exception) {
                resources.put(id, RenderTextureResource.averageColorFallback(id, definition.orElseThrow(),
                        "Texture decode failed; using average RGB: " + exception.getMessage()));
                continue;
            }
            resources.put(id, pixels.isPresent()
                    ? RenderTextureResource.from(id, definition.orElseThrow(), OSRS_TEXTURE_SIZE, pixels.orElseThrow())
                    : RenderTextureResource.averageColorFallback(id, definition.orElseThrow(),
                    "Texture pixels were not available from the cache provider; using average RGB"));
        }
        return Map.copyOf(resources);
    }
}
