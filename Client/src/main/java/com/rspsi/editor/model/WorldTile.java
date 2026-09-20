package com.rspsi.editor.model;

/**
 * Absolute OSRS world tile coordinate.
 *
 * <p>Viewport picking, navigation, scene overlays and cross-region services
 * use this type. Convert through {@link WorldWindow} before touching a
 * {@link WorldDocument}.</p>
 */
public record WorldTile(int plane, int x, int y) {
    public WorldTile {
        if (plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("World tile coordinates cannot be negative");
        }
    }

    public WorldTileAddress address() {
        return WorldTileAddress.of(x, y, plane);
    }
}
