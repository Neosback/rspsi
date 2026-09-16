package com.rspsi.editor.debug;

/**
 * Independent debug views that may be enabled by a workspace or frontend.
 * These names describe world semantics; they do not prescribe colors or
 * rendering technology.
 */
public enum DebugOverlayMode {
    TILE_GRID,
    CHUNK_GRID,
    REGION_GRID,
    LOADED_WORLD_WINDOW,
    TILE_FLAGS,
    COLLISION,
    BRIDGE_LINKS,
    OBJECTS,
    HEIGHTS,
    OVERLAY_SHAPES
}
