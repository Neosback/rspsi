package com.rspsi.editor;

/**
 * Neutral persistence boundary for a loaded multi-region editor window.
 *
 * <p>The editor core owns region/session state; cache adapters decide how a
 * group of dirty regions is encoded and flushed.</p>
 */
@FunctionalInterface
public interface WorldRegionSaveHandler {
    void save(WorldRegionSessionWindow window);
}
