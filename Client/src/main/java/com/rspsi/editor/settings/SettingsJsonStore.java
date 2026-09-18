package com.rspsi.editor.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned JSON persistence for the canonical typed settings store. */
public final class SettingsJsonStore {
    public static final int CURRENT_SCHEMA = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SettingsJsonStore() {
    }

    /** Loads known values and returns unknown or malformed entries for diagnostics. */
    public static List<String> load(Path file, SettingsStore store) {
        Objects.requireNonNull(file, "settings file");
        Objects.requireNonNull(store, "settings store");
        if (!Files.isRegularFile(file)) return List.of();
        List<String> ignored = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(Files.readString(file));
            JsonNode scopes = root == null ? null : root.get("scopes");
            if (scopes == null || !scopes.isObject()) return List.of("missing scopes object");
            scopes.fields().forEachRemaining(scopeEntry -> loadScope(
                    scopeEntry.getKey(), scopeEntry.getValue(), store, ignored));
            return List.copyOf(ignored);
        } catch (IOException | RuntimeException error) {
            return List.of("unable to read settings: " + error.getMessage());
        }
    }

    public static void save(Path file, SettingsStore store) {
        Objects.requireNonNull(file, "settings file");
        Objects.requireNonNull(store, "settings store");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", CURRENT_SCHEMA);
        ObjectNode scopes = root.putObject("scopes");
        for (SettingScope scope : SettingScope.values()) {
            ObjectNode values = scopes.putObject(scope.name());
            for (Map.Entry<SettingKey<?>, Object> entry : store.values(scope).entrySet()) {
                values.set(entry.getKey().id(), MAPPER.valueToTree(entry.getValue()));
            }
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n");
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to persist settings: " + file, error);
        }
    }

    private static void loadScope(String name, JsonNode values, SettingsStore store,
                                  List<String> ignored) {
        final SettingScope scope;
        try {
            scope = SettingScope.valueOf(name);
        } catch (RuntimeException error) {
            ignored.add("unknown settings scope: " + name);
            return;
        }
        if (!values.isObject()) {
            ignored.add("settings scope is not an object: " + name);
            return;
        }
        values.fields().forEachRemaining(entry -> {
            try {
                SettingSpec<?> specification = store.registry().specification(entry.getKey());
                Object value = decode(entry.getValue(), specification.key().valueType());
                store.set(scope, specification.key(), value);
            } catch (RuntimeException error) {
                ignored.add("ignored setting " + name + "." + entry.getKey()
                        + ": " + error.getMessage());
            }
        });
    }

    private static Object decode(JsonNode value, Class<?> type) {
        if (type == Boolean.class) return value.booleanValue();
        if (type == Integer.class) return value.intValue();
        if (type == Double.class) return value.doubleValue();
        if (type == String.class) return value.textValue();
        if (type.isEnum()) return Enum.valueOf(type.asSubclass(Enum.class), value.textValue());
        throw new IllegalArgumentException("unsupported setting type: " + type.getName());
    }
}
