package com.rspsi.editor.settings;

import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.render.SceneVisibilityPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsStoreTest {
    @Test
    void renderSettingsHaveDeclaredConsumers() {
        SettingsContractValidator.validateOrThrow(
                RenderSettingKeys.registry(), RenderSettingKeys.consumerCatalog());
    }

    @Test
    void contractRejectsAnUnconsumedRegisteredSetting() {
        SettingsRegistry registry = new SettingsRegistry();
        SettingKey<Boolean> key = new SettingKey<>("test.unconsumed", Boolean.class);
        registry.register(SettingSpec.of(key, false, SettingScope.GLOBAL,
                "Unconsumed", "Test setting", java.util.Set.of(SettingInvalidation.NONE)));

        assertThrows(IllegalStateException.class, () ->
                SettingsContractValidator.validateOrThrow(registry, new SettingConsumerCatalog()));
    }

    @Test
    void contractRejectsAnUnknownConsumerReference() {
        SettingsRegistry registry = new SettingsRegistry();
        SettingKey<Boolean> key = new SettingKey<>("test.unknown", Boolean.class);
        SettingConsumerCatalog consumers = new SettingConsumerCatalog();
        consumers.register("test", key);

        assertThrows(IllegalStateException.class, () ->
                SettingsContractValidator.validateOrThrow(registry, consumers));
    }

    @Test
    void layersResolveInDocumentedPrecedenceAndEmitInvalidation() {
        SettingsStore store = new SettingsStore(RenderSettingKeys.registry());
        List<SettingChange> changes = new ArrayList<>();
        store.addListener(changes::add);

        store.set(SettingScope.GLOBAL, RenderSettingKeys.BRIGHTNESS, 1.25);
        store.set(SettingScope.VIEWPORT, RenderSettingKeys.BRIGHTNESS, 2.0);

        assertEquals(2.0, store.snapshot().get(RenderSettingKeys.BRIGHTNESS));
        assertEquals(2, changes.size());
        assertEquals(SettingInvalidation.REDRAW,
                changes.get(1).invalidations().iterator().next());
    }

    @Test
    void typedAndRangedValuesAreRejectedBeforeTheyReachRenderConfig() {
        SettingsStore store = new SettingsStore(RenderSettingKeys.registry());

        assertThrows(IllegalArgumentException.class,
                () -> store.set(RenderSettingKeys.ACTIVE_PLANE, 4));
        assertThrows(IllegalArgumentException.class,
                () -> store.set(SettingScope.VIEWPORT, RenderSettingKeys.PLANE_SELECTION, "plane-0"));
    }

    @Test
    void transientOverrideCanBeRemovedWithoutChangingProjectValue() {
        SettingsStore store = new SettingsStore(RenderSettingKeys.registry());
        store.set(SettingScope.PROJECT, RenderSettingKeys.PLANE_SELECTION,
                SceneVisibilityPolicy.PlaneSelection.AUTHORED_PLANE);
        store.set(SettingScope.TRANSIENT, RenderSettingKeys.PLANE_SELECTION,
                SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE);

        assertEquals(SceneVisibilityPolicy.PlaneSelection.EFFECTIVE_PLANE,
                store.snapshot().get(RenderSettingKeys.PLANE_SELECTION));
        store.clear(SettingScope.TRANSIENT, RenderSettingKeys.PLANE_SELECTION);
        assertEquals(SceneVisibilityPolicy.PlaneSelection.AUTHORED_PLANE,
                store.snapshot().get(RenderSettingKeys.PLANE_SELECTION));
    }
}
