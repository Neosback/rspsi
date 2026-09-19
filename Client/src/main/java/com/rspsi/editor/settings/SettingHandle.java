package com.rspsi.editor.settings;

import java.util.Objects;

/**
 * Owns exactly one dynamically-registered setting. Closing it unregisters the
 * setting and clears any stored overrides; safe to close more than once.
 */
public final class SettingHandle implements AutoCloseable {
    private final SettingsService service;
    private final SettingKey<?> key;
    private boolean closed;

    SettingHandle(SettingsService service, SettingKey<?> key) {
        this.service = Objects.requireNonNull(service, "settings service");
        this.key = Objects.requireNonNull(key, "setting key");
    }

    public SettingKey<?> key() {
        return key;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        service.unregister(key);
    }
}
