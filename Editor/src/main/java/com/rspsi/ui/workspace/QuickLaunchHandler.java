package com.rspsi.ui.workspace;

/** UI callbacks into the existing map-opening workflows. */
public interface QuickLaunchHandler {
    void openLocalCache();
    void openCoordinates(String value);
    void openRegionId(String value);
    void createBlankCanvas();
    void openProject();
}
