package com.rspsi.ui.workspace;

import com.rspsi.options.Options;
import com.rspsi.editor.render.RenderConfig;
import com.rspsi.editor.render.RenderConfigCompiler;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import javafx.beans.value.ChangeListener;

/**
 * Temporary compatibility bridge from legacy JavaFX options to the typed
 * renderer settings. New renderer code must consume {@link RenderConfig}, not
 * read {@link Options} directly. The bridge can be removed after the active
 * settings panels are registry-backed.
 */
final class LegacyRenderSettingsAdapter {
    private LegacyRenderSettingsAdapter() {}

    static RenderConfig capture() {
        return new RenderConfigCompiler().compile(captureSnapshot());
    }

    static SettingsStore createStore() {
        SettingsStore store = new SettingsStore(RenderSettingKeys.registry());
        store.set(RenderSettingKeys.OBJECTS_VISIBLE, Options.showObjects.get());
        store.set(RenderSettingKeys.HIDDEN_TILES_VISIBLE, Options.showHiddenTiles.get());
        store.set(RenderSettingKeys.COLLISION_VISIBLE, collisionVisible());
        store.set(RenderSettingKeys.BRIDGE_TILES_VISIBLE, true);
        store.set(RenderSettingKeys.ROOFS_VISIBLE, !Options.showForceLowestPlaneFlag.get());
        return store;
    }

    /**
     * Keeps the compatibility UI and the neutral renderer settings in sync
     * during the migration away from static Options properties.
     */
    static LegacyBinding bindOptions(SettingsStore store) {
        ChangeListener<Boolean> objects = (observable, oldValue, newValue) ->
                store.set(RenderSettingKeys.OBJECTS_VISIBLE, newValue);
        ChangeListener<Boolean> hidden = (observable, oldValue, newValue) ->
                store.set(RenderSettingKeys.HIDDEN_TILES_VISIBLE, newValue);
        ChangeListener<Boolean> roofs = (observable, oldValue, newValue) ->
                store.set(RenderSettingKeys.ROOFS_VISIBLE, !newValue);
        ChangeListener<Boolean> collision = (observable, oldValue, newValue) ->
                store.set(RenderSettingKeys.COLLISION_VISIBLE, collisionVisible());
        Options.showObjects.addListener(objects);
        Options.showHiddenTiles.addListener(hidden);
        Options.showForceLowestPlaneFlag.addListener(roofs);
        Options.showBlockedFlag.addListener(collision);
        Options.showBridgeFlag.addListener(collision);
        Options.showLowerZFlag.addListener(collision);
        return new LegacyBinding(store, objects, hidden, roofs, collision);
    }

    private static SettingsSnapshot captureSnapshot() {
        return RenderSettingKeys.registry().defaults()
                .with(RenderSettingKeys.OBJECTS_VISIBLE, Options.showObjects.get())
                .with(RenderSettingKeys.HIDDEN_TILES_VISIBLE, Options.showHiddenTiles.get())
                .with(RenderSettingKeys.COLLISION_VISIBLE, collisionVisible())
                .with(RenderSettingKeys.BRIDGE_TILES_VISIBLE, true)
                .with(RenderSettingKeys.ROOFS_VISIBLE, !Options.showForceLowestPlaneFlag.get());
    }

    private static boolean collisionVisible() {
        return Options.showBlockedFlag.get() || Options.showBridgeFlag.get()
                || Options.showForceLowestPlaneFlag.get() || Options.showLowerZFlag.get();
    }

    static final class LegacyBinding implements AutoCloseable {
        private final SettingsStore store;
        private final ChangeListener<Boolean> objects;
        private final ChangeListener<Boolean> hidden;
        private final ChangeListener<Boolean> roofs;
        private final ChangeListener<Boolean> collision;

        private LegacyBinding(SettingsStore store, ChangeListener<Boolean> objects,
                              ChangeListener<Boolean> hidden, ChangeListener<Boolean> roofs,
                              ChangeListener<Boolean> collision) {
            this.store = store;
            this.objects = objects;
            this.hidden = hidden;
            this.roofs = roofs;
            this.collision = collision;
        }

        @Override
        public void close() {
            Options.showObjects.removeListener(objects);
            Options.showHiddenTiles.removeListener(hidden);
            Options.showForceLowestPlaneFlag.removeListener(roofs);
            Options.showBlockedFlag.removeListener(collision);
            Options.showBridgeFlag.removeListener(collision);
            Options.showLowerZFlag.removeListener(collision);
        }
    }
}
