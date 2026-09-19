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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * ReferenceProvider that indexes OpenRune Kotlin content modules (e.g. content/*)
 * to locate server scripts referencing specific objects, NPCs, or interfaces.
 */
public final class OpenRuneReferenceProvider implements ReferenceProvider {
    private final Path projectRoot;
    private final List<ContentReference> allReferences = new ArrayList<>();
    private final Map<String, List<ContentReference>> referencesBySymbol = new HashMap<>();
    private final Map<Integer, List<ContentReference>> referencesById = new HashMap<>();

    public OpenRuneReferenceProvider(Path projectRoot) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        indexContent();
    }

    private void indexContent() {
        Path contentDir = projectRoot.resolve("content");
        if (!Files.isDirectory(contentDir)) return;

        try (Stream<Path> stream = Files.walk(contentDir)) {
            stream.filter(p -> p.toString().endsWith(".kt"))
                    .forEach(this::indexKotlinFile);
        } catch (IOException ignored) {
        }
    }

    private void indexKotlinFile(Path file) {
        String relative = projectRoot.relativize(file).toString();
        String module = "content";
        Path contentDir = projectRoot.resolve("content");
        if (file.startsWith(contentDir)) {
            Path relToContent = contentDir.relativize(file);
            if (relToContent.getNameCount() > 1) {
                module = relToContent.getName(0).toString();
            }
        }

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                indexLine(line, lineNum, module, relative);
                lineNum++;
            }
        } catch (IOException ignored) {
        }
    }

    private void indexLine(String line, int lineNum, String module, String relativePath) {
        // Look for loc.xxx, npc.xxx, item.xxx patterns
        scanPattern(line, lineNum, module, relativePath, "loc.", SymbolNamespace.LOC);
        scanPattern(line, lineNum, module, relativePath, "npc.", SymbolNamespace.NPC);
        scanPattern(line, lineNum, module, relativePath, "item.", SymbolNamespace.ITEM);
    }

    private void scanPattern(String line, int lineNum, String module, String relativePath,
                            String prefix, SymbolNamespace namespace) {
        int idx = 0;
        while ((idx = line.indexOf(prefix, idx)) != -1) {
            int end = idx + prefix.length();
            while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) {
                end++;
            }
            String bareName = line.substring(idx + prefix.length(), end);
            if (!bareName.isEmpty()) {
                String fullSymbol = prefix + bareName;
                ContentReference ref = new ContentReference(
                        fullSymbol, namespace, -1, module, relativePath, lineNum, line.trim(), "Script"
                );
                allReferences.add(ref);
                referencesBySymbol.computeIfAbsent(fullSymbol.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(ref);
                referencesBySymbol.computeIfAbsent(bareName.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(ref);
            }
            idx = end;
        }
    }

    /** Manually registers a reference for testing or programmatic addition. */
    public void addReference(ContentReference ref) {
        Objects.requireNonNull(ref, "ref");
        allReferences.add(ref);
        referencesBySymbol.computeIfAbsent(ref.targetSymbol().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(ref);
        if (ref.id() >= 0) {
            referencesById.computeIfAbsent(ref.id(), k -> new ArrayList<>()).add(ref);
        }
    }

    @Override
    public String id() {
        return "openrune.references";
    }

    @Override
    public List<ContentReference> referencesFor(SymbolNamespace namespace, int id, String symbolicName) {
        List<ContentReference> matches = new ArrayList<>();
        if (symbolicName != null && !symbolicName.isBlank()) {
            List<ContentReference> byName = referencesBySymbol.get(symbolicName.toLowerCase(Locale.ROOT));
            if (byName != null) matches.addAll(byName);
        }
        if (id >= 0) {
            List<ContentReference> byId = referencesById.get(id);
            if (byId != null) matches.addAll(byId);
        }
        return Collections.unmodifiableList(matches);
    }

    @Override
    public List<ContentReference> referencesInModule(String module) {
        return allReferences.stream()
                .filter(r -> r.module().equalsIgnoreCase(module))
                .toList();
    }

    @Override
    public int totalReferenceCount() {
        return allReferences.size();
    }
}
