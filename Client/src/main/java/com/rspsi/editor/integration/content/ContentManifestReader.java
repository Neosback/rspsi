package com.rspsi.editor.integration.content;

import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lenient reader for content-manifest.toml sidecars with visible diagnostics. */
public final class ContentManifestReader {
    public ContentManifest read(Path path, ParseDiagnostics diagnostics) {
        try {
            TomlParseResult toml = Toml.parse(path);
            toml.errors().forEach(error ->
                    diagnostics.error("manifest.toml", path,
                            error.position() == null ? 0 : error.position().line(),
                            error.getMessage()));
            if (toml.hasErrors()) {
                diagnostics.skipped();
                return null;
            }

            Long manifestValue = toml.getLong("manifest_version");
            int manifestVersion = manifestValue == null ? 1 : Math.toIntExact(manifestValue);
            String id = toml.getString("id");
            if (id == null) id = "";
            String name = toml.getString("name");
            if (name == null) name = id;
            String homepage = toml.getString("homepage");
            if (homepage == null) homepage = "";
            List<String> authors = strings(toml.getArray("authors"));

            EnumSet<ContentCapability> capabilities = EnumSet.noneOf(ContentCapability.class);
            for (String value : strings(toml.getArray("capabilities"))) {
                capabilities.add(ContentCapability.fromId(value));
            }

            Map<String, String> schemas = stringTable(toml.getTable("schemas"));
            Map<String, List<String>> resources = listTable(toml.getTable("resources"));
            ContentManifest manifest = new ContentManifest(
                    manifestVersion, id, name, authors, homepage,
                    capabilities, schemas, resources, path);
            if (manifestVersion > ContentManifest.CURRENT_MANIFEST_VERSION) {
                diagnostics.warning("manifest.newer", path, 0,
                        "Manifest version " + manifestVersion
                                + " is newer than Studio supports ("
                                + ContentManifest.CURRENT_MANIFEST_VERSION + ")");
            }
            diagnostics.imported();
            return manifest;
        } catch (RuntimeException | java.io.IOException failure) {
            diagnostics.error("manifest.read", path, 0,
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage());
            diagnostics.skipped();
            return null;
        }
    }

    private static List<String> strings(TomlArray array) {
        if (array == null) return List.of();
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            Object value = array.get(index);
            if (value instanceof String text && !text.isBlank()) values.add(text.trim());
        }
        return List.copyOf(values);
    }

    private static Map<String, String> stringTable(TomlTable table) {
        if (table == null) return Map.of();
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object value = table.get(key);
            if (value != null) values.put(key, String.valueOf(value));
        }
        return Map.copyOf(values);
    }

    private static Map<String, List<String>> listTable(TomlTable table) {
        if (table == null) return Map.of();
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String key : table.keySet()) {
            Object raw = table.get(key);
            if (raw instanceof String text) {
                values.put(key, List.of(text));
            } else if (raw instanceof TomlArray array) {
                values.put(key, strings(array));
            }
        }
        return Map.copyOf(values);
    }
}
