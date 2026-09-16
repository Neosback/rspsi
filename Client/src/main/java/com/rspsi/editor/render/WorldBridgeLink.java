package com.rspsi.editor.render;

import com.rspsi.editor.model.WorldTileAddress;

import java.util.Objects;

/** World-space bridge relationship for a multi-region render window. */
public record WorldBridgeLink(WorldTileAddress authored, WorldTileAddress effective) {
    public WorldBridgeLink {
        authored = Objects.requireNonNull(authored, "authored");
        effective = Objects.requireNonNull(effective, "effective");
        if (authored.worldX() != effective.worldX() || authored.worldY() != effective.worldY()
                || effective.plane() != authored.plane() - 1) {
            throw new IllegalArgumentException("Invalid world bridge relationship");
        }
    }
}
