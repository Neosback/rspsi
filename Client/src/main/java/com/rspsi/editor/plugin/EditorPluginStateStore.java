package com.rspsi.editor.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persisted enable/disable state for editor plugin candidates.
 *
 * <p>The store records only user intent: a plugin id is present in the
 * disabled set when the user turned it off. Cascade-disabling dependents is
 * derived by {@link EditorPluginLifecycleManager}, never stored as user
 * intent, so re-enabling a dependency does not silently re-enable plugins
 * the user disabled individually.</p>
 *
 * <p>State lives in {@code ~/.rspsi/plugins.json} by default and survives
 * restarts. A missing or malformed file is treated as "everything enabled"
 * and is overwritten on the next successful change.</p>
 */
public final class EditorPluginStateStore {
    private final Path file;
    private final LinkedHashSet<String> disabled;

    private EditorPluginStateStore(Path file, LinkedHashSet<String> disabled) {
        this.file = Objects.requireNonNull(file, "file");
        this.disabled = disabled;
    }

    /** Reads persisted state; a missing or unreadable file yields an empty store. */
    public static EditorPluginStateStore load(Path file) {
        Objects.requireNonNull(file, "file");
        LinkedHashSet<String> disabled = new LinkedHashSet<>();
        if (Files.isRegularFile(file)) {
            try {
                String json = Files.readString(file);
                JsonNode root = new ObjectMapper().readTree(json);
                JsonNode array = root == null ? null : root.get("disabled");
                if (array != null && array.isArray()) {
                    array.forEach(entry -> {
                        if (entry != null && entry.isTextual() && !entry.textValue().isBlank()) {
                            disabled.add(entry.textValue().trim());
                        }
                    });
                }
            } catch (IOException | RuntimeException ignored) {
                disabled.clear();
            }
        }
        return new EditorPluginStateStore(file, disabled);
    }

    /** Returns the store backed by the user-level {@code ~/.rspsi/plugins.json}. */
    public static EditorPluginStateStore defaultStore() {
        return load(Paths.get(System.getProperty("user.home"), ".rspsi", "plugins.json"));
    }

    /** True when the user has not disabled the plugin. */
    public boolean isEnabled(String pluginId) {
        return !disabled.contains(Objects.requireNonNull(pluginId, "pluginId").trim());
    }

    /** Unmodifiable snapshot of the user-disabled plugin ids. */
    public Set<String> disabledIds() {
        return Set.copyOf(disabled);
    }

    /** Replaces the disabled set and persists it. */
    public synchronized void replaceDisabled(Collection<String> ids) {
        LinkedHashSet<String> next = new LinkedHashSet<>();
        for (String id : ids) {
            String value = Objects.requireNonNull(id, "plugin id").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Plugin id cannot be empty");
            }
            next.add(value);
        }
        persist(next);
        disabled.clear();
        disabled.addAll(next);
    }

    Path file() {
        return file;
    }

    private void persist(LinkedHashSet<String> next) {
        StringBuilder json = new StringBuilder("{\n  \"disabled\": [");
        int index = 0;
        for (String id : next) {
            json.append(index++ == 0 ? "\n" : ",\n")
                    .append("    \"").append(escape(id)).append('"');
        }
        json.append(next.isEmpty() ? "\n" : "\n  ").append("]\n}\n");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, json.toString());
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to persist plugin state: " + file, error);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static List<String> normalized(Collection<String> ids) {
        return ids.stream().map(String::trim).distinct().toList();
    }
}
