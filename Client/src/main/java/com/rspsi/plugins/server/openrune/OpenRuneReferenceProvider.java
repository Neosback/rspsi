package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.integration.reference.ContentReference;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.symbols.SymbolNamespace;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ReferenceProvider that indexes declarative OpenRune TOML/JSON artifacts.
 *
 * <p>It deliberately does not read Kotlin source. Content authors expose
 * richer references through data manifests/adapters instead of source parsing.</p>
 */
public final class OpenRuneReferenceProvider implements ReferenceProvider {
    private final Path projectRoot;
    private final List<ContentReference> allReferences = new ArrayList<>();
    private final Map<String, List<ContentReference>> referencesBySymbol = new HashMap<>();
    private final Map<Integer, List<ContentReference>> referencesById = new HashMap<>();

    public OpenRuneReferenceProvider(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        indexDeclarativeContent();
    }

    private void indexDeclarativeContent() {
        OpenRuneContentCatalog catalog;
        try {
            catalog = new OpenRuneContentCatalog(projectRoot);
        } catch (RuntimeException ignored) {
            return;
        }

        Set<Path> files = new LinkedHashSet<>();
        catalog.discovery().artifacts().forEach(artifact -> files.add(artifact.path()));
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".toml") || name.endsWith(".json")) indexDataFile(file);
        }
    }

    private void indexDataFile(Path file) {
        String relative = projectRoot.relativize(file).toString();
        String module = moduleName(file);
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                indexLine(line, lineNum++, module, relative);
            }
        } catch (IOException ignored) {
        }
    }

    private String moduleName(Path file) {
        Path content = projectRoot.resolve("content");
        if (file.startsWith(content)) {
            Path relative = content.relativize(file);
            if (relative.getNameCount() > 1) return relative.getName(0).toString();
        }
        Path parent = file.getParent();
        return parent == null ? "data" : parent.getFileName().toString();
    }

    private void indexLine(String line, int lineNum, String module, String relativePath) {
        scanPattern(line, lineNum, module, relativePath, "loc.", SymbolNamespace.LOC);
        scanPattern(line, lineNum, module, relativePath, "npc.", SymbolNamespace.NPC);
        scanPattern(line, lineNum, module, relativePath, "item.", SymbolNamespace.ITEM);
    }

    private void scanPattern(String line, int lineNum, String module, String relativePath,
                             String prefix, SymbolNamespace namespace) {
        int idx = 0;
        while ((idx = line.indexOf(prefix, idx)) != -1) {
            int end = idx + prefix.length();
            while (end < line.length()
                    && (Character.isLetterOrDigit(line.charAt(end))
                    || line.charAt(end) == '_')) {
                end++;
            }
            String bareName = line.substring(idx + prefix.length(), end);
            if (!bareName.isEmpty()) {
                String fullSymbol = prefix + bareName;
                ContentReference ref = new ContentReference(
                        fullSymbol, namespace, -1, module, relativePath,
                        lineNum, line.trim(), "Data");
                allReferences.add(ref);
                referencesBySymbol.computeIfAbsent(
                        fullSymbol.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(ref);
                referencesBySymbol.computeIfAbsent(
                        bareName.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(ref);
            }
            idx = end;
        }
    }

    public void addReference(ContentReference ref) {
        Objects.requireNonNull(ref, "ref");
        allReferences.add(ref);
        referencesBySymbol.computeIfAbsent(
                ref.targetSymbol().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(ref);
        if (ref.id() >= 0) referencesById.computeIfAbsent(ref.id(), key -> new ArrayList<>()).add(ref);
    }

    @Override public String id() { return "openrune.references"; }

    @Override
    public List<ContentReference> referencesFor(
            SymbolNamespace namespace, int id, String symbolicName) {
        List<ContentReference> matches = new ArrayList<>();
        if (symbolicName != null && !symbolicName.isBlank()) {
            List<ContentReference> byName =
                    referencesBySymbol.get(symbolicName.toLowerCase(Locale.ROOT));
            if (byName != null) matches.addAll(byName);
        }
        if (id >= 0) {
            List<ContentReference> byId = referencesById.get(id);
            if (byId != null) matches.addAll(byId);
        }
        return Collections.unmodifiableList(matches.stream()
                .filter(ref -> namespace == null || ref.namespace() == namespace)
                .distinct().toList());
    }

    @Override
    public List<ContentReference> referencesInModule(String module) {
        return allReferences.stream()
                .filter(ref -> ref.module().equalsIgnoreCase(module)).toList();
    }

    @Override public int totalReferenceCount() { return allReferences.size(); }
}
