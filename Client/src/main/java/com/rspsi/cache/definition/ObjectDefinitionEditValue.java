package com.rspsi.cache.definition;

import java.util.Locale;
import java.util.Objects;

/**
 * Typed scalar value used by backend-neutral definition edit transactions.
 *
 * <p>Definition collections and backend-specific objects intentionally do not
 * cross this boundary. Complex fields get dedicated edit operations later.</p>
 */
public record ObjectDefinitionEditValue(
        ObjectDefinitionRawView.ValueType type,
        String value
) {
    public ObjectDefinitionEditValue {
        type = Objects.requireNonNull(type, "type");
        value = Objects.requireNonNull(value, "value");
        if (type != ObjectDefinitionRawView.ValueType.STRING
                && type != ObjectDefinitionRawView.ValueType.INTEGER
                && type != ObjectDefinitionRawView.ValueType.LONG
                && type != ObjectDefinitionRawView.ValueType.BOOLEAN) {
            throw new IllegalArgumentException(
                    "Definition edit value must be a scalar type: " + type);
        }
        if (type == ObjectDefinitionRawView.ValueType.INTEGER) {
            Integer.parseInt(value);
        } else if (type == ObjectDefinitionRawView.ValueType.LONG) {
            Long.parseLong(value);
        } else if (type == ObjectDefinitionRawView.ValueType.BOOLEAN) {
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!normalized.equals("true") && !normalized.equals("false")) {
                throw new IllegalArgumentException("Boolean edit value must be true or false");
            }
            value = normalized;
        }
    }

    public static ObjectDefinitionEditValue stringValue(String value) {
        return new ObjectDefinitionEditValue(
                ObjectDefinitionRawView.ValueType.STRING,
                Objects.requireNonNull(value, "value"));
    }

    public static ObjectDefinitionEditValue intValue(int value) {
        return new ObjectDefinitionEditValue(
                ObjectDefinitionRawView.ValueType.INTEGER,
                Integer.toString(value));
    }

    public static ObjectDefinitionEditValue longValue(long value) {
        return new ObjectDefinitionEditValue(
                ObjectDefinitionRawView.ValueType.LONG,
                Long.toString(value));
    }

    public static ObjectDefinitionEditValue booleanValue(boolean value) {
        return new ObjectDefinitionEditValue(
                ObjectDefinitionRawView.ValueType.BOOLEAN,
                Boolean.toString(value));
    }
}
