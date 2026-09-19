package com.rspsi.editor.settings;

import com.rspsi.editor.plugin.ContributionOwner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Owned front door onto a {@link SettingsStore}: settings register through here with a
 * {@link ContributionOwner} and get back an {@link AutoCloseable} {@link SettingHandle}.
 * Closing the handle (typically via {@code EditorPluginContext.track(handle)}) unregisters
 * the setting and clears its stored overrides, so a plugin's settings never outlive it.
 */
public final class SettingsService {
    private final SettingsStore store;
    private final Map<String, ContributionOwner> owners = new LinkedHashMap<>();

    public SettingsService(SettingsStore store) {
        this.store = Objects.requireNonNull(store, "settings store");
    }

    public SettingsStore store() {
        return store;
    }

    public <T> SettingHandle register(ContributionOwner owner, SettingSpec<T> spec) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "setting spec");
        store.registry().register(spec);
        owners.put(spec.key().id(), owner);
        return new SettingHandle(this, spec.key());
    }

    public <T> T get(SettingKey<T> key) {
        return store.snapshot().get(key);
    }

    public <T> void set(SettingKey<T> key, T value) {
        store.set(key, value);
    }

    public SettingsSnapshot snapshot() {
        return store.snapshot();
    }

    public Optional<ContributionOwner> ownerOf(String settingId) {
        return Optional.ofNullable(owners.get(Objects.requireNonNull(settingId, "setting id")));
    }

    public List<SettingSpec<?>> settingsOwnedBy(ContributionOwner owner) {
        Objects.requireNonNull(owner, "owner");
        return store.registry().specifications().stream()
                .filter(spec -> owner.equals(owners.get(spec.key().id())))
                .toList();
    }

    /** Package-visible: callers unregister only through {@link SettingHandle#close()}. */
    void unregister(SettingKey<?> key) {
        // Clear stored overrides first: SettingsStore#clear validates the key still
        // resolves in the registry, so the registry removal must happen last.
        for (SettingScope scope : SettingScope.values()) {
            store.clear(scope, key);
        }
        store.registry().unregister(key);
        owners.remove(key.id());
    }
}
