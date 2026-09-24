package com.rspsi.editor.integration.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable server-object semantic overlay snapshot. */
public final class ServerObjectSemanticIndex {
    private final List<ServerObjectSemanticOverlay> overlays;
    private final Map<String, ServerObjectSemanticOverlay> bySymbol;
    private final Map<Integer, ServerObjectSemanticOverlay> byId;
    private final List<String> diagnostics;

    public ServerObjectSemanticIndex(
            List<ServerObjectSemanticOverlay> overlays,
            List<String> diagnostics) {
        this.overlays = List.copyOf(overlays == null ? List.of() : overlays);
        this.diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);

        LinkedHashMap<String, ServerObjectSemanticOverlay> symbols = new LinkedHashMap<>();
        LinkedHashMap<Integer, ServerObjectSemanticOverlay> ids = new LinkedHashMap<>();
        for (ServerObjectSemanticOverlay overlay : this.overlays) {
            symbols.putIfAbsent(
                    SemanticSymbolNames.canonical(overlay.objectSymbol()), overlay);
            if (overlay.resolved()) ids.putIfAbsent(overlay.objectId(), overlay);
        }
        bySymbol = Map.copyOf(symbols);
        byId = Map.copyOf(ids);
    }

    public List<ServerObjectSemanticOverlay> overlays() {
        return overlays;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Optional<ServerObjectSemanticOverlay> bySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        return Optional.ofNullable(
                bySymbol.get(SemanticSymbolNames.canonical(symbol)));
    }

    public Optional<ServerObjectSemanticOverlay> byId(int id) {
        if (id < 0) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }
}
