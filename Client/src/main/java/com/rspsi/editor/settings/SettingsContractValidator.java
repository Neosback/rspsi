package com.rspsi.editor.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Validates that the typed settings registry and consumer declarations agree. */
public final class SettingsContractValidator {
    private SettingsContractValidator() {}

    public static void validateOrThrow(SettingsRegistry registry, SettingConsumerCatalog consumers) {
        Objects.requireNonNull(registry, "settings registry");
        Objects.requireNonNull(consumers, "setting consumers");
        List<String> errors = new ArrayList<>();

        consumers.consumers().forEach((consumer, keys) -> keys.forEach(key -> {
            try {
                registry.specification(key);
            } catch (RuntimeException error) {
                errors.add("consumer '" + consumer + "' references unknown setting '" + key.id() + "'");
            }
        }));

        consumers.consumedKeys();
        registry.specifications().forEach(specification -> {
            if (!consumers.consumedKeys().contains(specification.key())) {
                errors.add("registered setting has no consumer: '" + specification.key().id() + "'");
            }
        });

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Settings contract failed:\n - " + String.join("\n - ", errors));
        }
    }
}
