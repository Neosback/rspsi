package com.rspsi.studio.ui;

import com.rspsi.editor.model.WorldObject;

/**
 * Shell-owned cross-tool navigation/actions exposed to panels and plugins.
 *
 * <p>Panels request an intent such as "inspect this object"; they do not reach
 * into sibling panel instances or auxiliary windows directly.</p>
 */
public interface StudioNavigation {
    StudioNavigation NONE = new StudioNavigation() {
        @Override public void inspectObject(WorldObject object) {}
        @Override public void editObject(WorldObject object) {}
    };

    void inspectObject(WorldObject object);

    void editObject(WorldObject object);
}
