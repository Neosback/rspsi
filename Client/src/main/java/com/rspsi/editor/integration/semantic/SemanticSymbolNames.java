package com.rspsi.editor.integration.semantic;

import java.util.Locale;

/** Canonical symbol identity shared by graph ingestion and queries. */
public final class SemanticSymbolNames {
    private SemanticSymbolNames() {
    }

    public static String canonical(String value) {
        if (value == null) return "";
        String symbol = value.trim().toLowerCase(Locale.ROOT);
        if (symbol.startsWith("obj.")) {
            return "item." + symbol.substring("obj.".length());
        }
        if (symbol.startsWith("varcon.")) {
            return "varc." + symbol.substring("varcon.".length());
        }
        return symbol;
    }

    public static boolean looksQualified(String value) {
        String symbol = canonical(value);
        int dot = symbol.indexOf('.');
        return dot > 0 && dot < symbol.length() - 1;
    }

    public static String namespace(String value) {
        String symbol = canonical(value);
        int dot = symbol.indexOf('.');
        return dot <= 0 ? "" : symbol.substring(0, dot);
    }

    public static String nodeId(String value) {
        String symbol = canonical(value);
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol cannot be empty");
        return "symbol:" + symbol;
    }
}
