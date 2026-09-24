package com.rspsi.editor.integration.semantic;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Source-controlled server semantics layered over one cache/world object identity.
 *
 * <p>The numeric object ID may be unresolved ({@code -1}) when the project's symbolic mappings
 * are incomplete. This model is read-only; {@code writableSource} means the fact originated from
 * an authored source file rather than a generated cache, not that Studio has an edit lens yet.</p>
 */
public record ServerObjectSemanticOverlay(
        String objectSymbol,
        int objectId,
        Optional<String> inheritSymbol,
        Optional<String> contentGroupSymbol,
        Map<String, String> params,
        SourceSpan blockSource,
        Map<String, SourceSpan> fieldSources,
        boolean writableSource) {

    public ServerObjectSemanticOverlay {
        objectSymbol = requireText(objectSymbol, "objectSymbol");
        if (objectId < -1) throw new IllegalArgumentException("objectId cannot be less than -1");
        inheritSymbol = clean(inheritSymbol);
        contentGroupSymbol = clean(contentGroupSymbol);
        params = Map.copyOf(params == null ? Map.of() : params);
        blockSource = Objects.requireNonNull(blockSource, "blockSource");
        fieldSources = Map.copyOf(fieldSources == null ? Map.of() : fieldSources);
    }

    public boolean resolved() {
        return objectId >= 0;
    }

    public Optional<SourceSpan> fieldSource(String field) {
        if (field == null) return Optional.empty();
        return Optional.ofNullable(fieldSources.get(field));
    }

    private static Optional<String> clean(Optional<String> value) {
        if (value == null || value.isEmpty()) return Optional.empty();
        String text = value.orElse("").trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String requireText(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return result;
    }
}
