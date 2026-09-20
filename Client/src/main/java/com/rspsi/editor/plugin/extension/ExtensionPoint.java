package com.rspsi.editor.plugin.extension;

import java.util.Objects;

/**
 * Stable typed extension point. Core can define well-known points while
 * independent plugins can share contracts without coupling to a frontend.
 */
public record ExtensionPoint<T>(String id, Class<T> type) {
    public ExtensionPoint {
        id = Objects.requireNonNull(id, "extension point id").trim();
        type = Objects.requireNonNull(type, "extension point type");
        if (id.isEmpty()) throw new IllegalArgumentException("Extension point id cannot be empty");
    }

    public static <T> ExtensionPoint<T> of(String id, Class<T> type) {
        return new ExtensionPoint<>(id, type);
    }
}
