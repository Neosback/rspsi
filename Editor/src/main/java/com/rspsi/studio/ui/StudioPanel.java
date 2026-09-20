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

    void render(StudioPanelContext context);
}
