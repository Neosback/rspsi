package com.rspsi.editor.model;

/**
 * Legacy local-document tile coordinate.
 *
 * @deprecated New viewport/navigation code must use WorldTile and new
 * document-facing code must use LocalTile. This compatibility type remains
 * temporarily for command/history/selection contracts while they migrate.
 */
@Deprecated
public record TileCoordinate(int plane, int x, int y) {
    public TileCoordinate {
        if (plane < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("Tile coordinates cannot be negative");
        }
    }
}
