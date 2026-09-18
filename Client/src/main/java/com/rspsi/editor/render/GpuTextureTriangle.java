package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/** Preserved model texture-triangle metadata associated with a world object. */
public record GpuTextureTriangle(
        WorldTileAddress tile,
        int objectId,
        TextureTriangle mapping
) {
    public GpuTextureTriangle {
        tile = Objects.requireNonNull(tile, "tile");
        mapping = Objects.requireNonNull(mapping, "mapping");
        if (objectId < 0) throw new IllegalArgumentException("Texture triangle object id cannot be negative");
    }
}
