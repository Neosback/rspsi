package com.rspsi.cache.definition;

import java.util.List;

/**
 * Backend-neutral raw decoded object-definition metadata for inspection.
 *
 * <p>This deliberately carries display-safe scalar/list values instead of
 * exposing a cache-library definition type. It is read-only: mutation and
 * serialization are separate capabilities so Studio can first establish a
 * trustworthy complete inspector across cache backends.</p>
 */
public record ObjectDefinitionRawView(
        int id,
        List<Field> fields,
        List<Param> params
) {
    public ObjectDefinitionRawView {
        if (id < 0) {
            throw new IllegalArgumentException("Object definition id cannot be negative");
        }
        fields = List.copyOf(fields == null ? List.of() : fields);
        params = List.copyOf(params == null ? List.of() : params);
    }

    public enum ValueType {
        STRING,
        INTEGER,
        LONG,
        BOOLEAN,
        LIST,
        MAP,
        NULL,
        OTHER
    }

    public record Field(
            String name,
            String opcode,
            ValueType type,
            String value
    ) {
        public Field {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Raw definition field needs a name");
            }
            opcode = opcode == null ? "" : opcode;
            type = type == null ? ValueType.OTHER : type;
            value = value == null ? "null" : value;
        }
    }

    public record Param(
            int id,
            ValueType type,
            String value
    ) {
        public Param {
            if (id < 0 || id > 0xFFFFFF) {
                throw new IllegalArgumentException("Param id outside unsigned-medium range: " + id);
            }
            type = type == null ? ValueType.OTHER : type;
            value = value == null ? "null" : value;
        }
    }
}
