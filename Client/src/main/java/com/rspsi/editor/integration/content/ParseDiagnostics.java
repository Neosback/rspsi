package com.rspsi.editor.integration.content;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mutable collector with an immutable snapshot for tolerant content readers. */
public final class ParseDiagnostics {
    private final List<ParseDiagnostic> entries = new ArrayList<>();
    private int imported;
    private int skipped;

    public void imported() { imported++; }
    public void skipped() { skipped++; }

    public void info(String code, Path path, int line, String message) {
        entries.add(new ParseDiagnostic(ParseDiagnostic.Severity.INFO, code, path, line, message));
    }

    public void warning(String code, Path path, int line, String message) {
        entries.add(new ParseDiagnostic(ParseDiagnostic.Severity.WARNING, code, path, line, message));
    }

    public void error(String code, Path path, int line, String message) {
        entries.add(new ParseDiagnostic(ParseDiagnostic.Severity.ERROR, code, path, line, message));
    }

    public int importedCount() { return imported; }
    public int skippedCount() { return skipped; }
    public List<ParseDiagnostic> entries() { return List.copyOf(entries); }
    public boolean hasErrors() {
        return entries.stream().anyMatch(value -> value.severity() == ParseDiagnostic.Severity.ERROR);
    }

    public Snapshot snapshot() {
        return new Snapshot(imported, skipped, List.copyOf(entries));
    }

    public record Snapshot(int imported, int skipped, List<ParseDiagnostic> entries) {
        public Snapshot {
            entries = List.copyOf(entries);
        }
    }
}
