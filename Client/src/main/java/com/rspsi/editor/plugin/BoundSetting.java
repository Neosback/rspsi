package com.rspsi.editor.plugin;

import com.rspsi.editor.settings.SettingHandle;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingSpec;
import com.rspsi.editor.settings.SettingsService;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Direct typed handle for reading and updating a dynamically registered setting.
 */
public final class BoundSetting<T> implements Supplier<T>, AutoCloseable {
    private final SettingsService service;
    private final SettingSpec<T> spec;
    private final SettingHandle handle;

    public BoundSetting(SettingsService service, SettingSpec<T> spec, SettingHandle handle) {
        this.service = Objects.requireNonNull(service, "service");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    public SettingKey<T> key() {
        return spec.key();
    }

    public SettingSpec<T> spec() {
        return spec;
    }

    public SettingHandle handle() {
        return handle;
    }

    @Override
    public T get() {
        return service.get(spec.key());
    }

    public void set(T value) {
        service.set(spec.key(), value);
    }

    @Override
    public void close() {
        handle.close();
    }
}
