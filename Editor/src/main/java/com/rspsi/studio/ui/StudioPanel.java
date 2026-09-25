package com.rspsi.studio.ui;

import com.rspsi.editor.ui.DockRegion;

import java.util.EnumSet;
import java.util.Set;

/**
 * Interface implemented by native Dear ImGui Studio panels.
 */
public interface StudioPanel {
    String id();
    String title();
    String icon();
    DockRegion preferredRegion();

    default Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    default int order() {
        return 0;
    }

    /**
     * Preferred total width when this panel owns the right sidebar.
     * Studio clamps the request so the viewport always keeps a usable minimum.
     */
    default float preferredRightSidebarWidth() {
        return 390.0f;
    }

    /**
     * Horizontal scrolling is forbidden by default. Panels should reflow controls,
     * wrap prose, or use structured rows. Opt in only for genuinely wide data such
     * as a matrix/timeline where horizontal position carries meaning.
     */
    default boolean allowHorizontalScroll() {
        return false;
    }

    void render(StudioPanelContext context);
}
