package com.rspsi.editor.tool;

import java.util.Objects;

/** UI-neutral description of one editable tool property. */
public record PropertyDescriptor(String id, String label, ValueType type, int minimum, int maximum) {
    public enum ValueType { INTEGER, DECIMAL, BOOLEAN, ENUM }

    public PropertyDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        if (minimum > maximum) {
            throw new IllegalArgumentException("Property minimum cannot exceed maximum");
        }
    }
}
