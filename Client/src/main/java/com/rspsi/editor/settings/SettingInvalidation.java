package com.rspsi.editor.settings;

/** Work that may become stale after a setting changes. */
public enum SettingInvalidation {
    NONE,
    REDRAW,
    VISIBILITY,
    LIGHTING,
    MATERIALS,
    GEOMETRY,
    GPU_RESOURCES,
    FRAMEBUFFER,
    SCENE
}
