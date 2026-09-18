package com.rspsi.editor.settings;

import java.util.Objects;

/** Stable, typed identity for a setting. */
public record SettingKey<T>(String id, Class<T> valueType) {
    public SettingKey {
        id = Objects.requireNonNull(id, "setting id").trim();
        valueType = Objects.requireNonNull(valueType, "setting value type");
        if (id.isEmpty()) throw new IllegalArgumentException("Setting id cannot be empty");
    }
}
