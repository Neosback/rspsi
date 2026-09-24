package com.rspsi.editor.integration.semantic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable source-semantic snapshot for one connected project. */
public record SemanticSourceIndex(
        List<SemanticSourceFact> facts,
        List<Path> files,
        List<String> diagnostics) {

    public SemanticSourceIndex {
        facts = List.copyOf(facts == null ? List.of() : facts);
        files = files == null ? List.of() : files.stream()
                .map(path -> Objects.requireNonNull(path, "file").toAbsolutePath().normalize())
                .distinct()
                .toList();
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<SemanticSourceFact> facts(SemanticFactKind kind) {
        Objects.requireNonNull(kind, "kind");
        return facts.stream().filter(fact -> fact.kind() == kind).toList();
    }

    public List<SemanticSourceFact> references(String symbol) {
        if (symbol == null || symbol.isBlank()) return List.of();
        String target = symbol.trim().toLowerCase(Locale.ROOT);
        List<SemanticSourceFact> matches = new ArrayList<>();
        for (SemanticSourceFact fact : facts) {
            if (fact.kind() != SemanticFactKind.SYMBOL_REFERENCE) continue;
            if (fact.name().toLowerCase(Locale.ROOT).equals(target)) matches.add(fact);
        }
        return List.copyOf(matches);
    }
}
