package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/** A renderer-neutral object projection with an explicit world-space address. */
public record WorldRenderObject(WorldTileAddress address, RenderObject object) {
    public WorldRenderObject {
        address = Objects.requireNonNull(address, "address");
        object = Objects.requireNonNull(object, "object");
        if (address.plane() != object.object().plane()
                || address.regionLocalX() != object.object().x()
                || address.regionLocalY() != object.object().y()) {
            throw new IllegalArgumentException("World object address does not match its canonical placement");
        }
    }
}
