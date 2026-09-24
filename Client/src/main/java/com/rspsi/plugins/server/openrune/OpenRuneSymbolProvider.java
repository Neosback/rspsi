package com.rspsi.plugins.server.openrune;

import com.rspsi.editor.symbols.Symbol;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.editor.symbols.SymbolProvider;
import com.rspsi.server.ServerContentKind;
import com.rspsi.server.ServerPathKey;
import com.rspsi.server.ServerProjectInspection;

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
import java.util.Optional;

/**
 * SymbolProvider that parses OpenRune RSCM mapping files and gamevals (.data/gamevals/*.rscm, gamevals.toml).
 */
public final class OpenRuneSymbolProvider implements SymbolProvider {
    private final Path projectRoot;
    private final Map<SymbolNamespace, Map<String, Symbol>> symbolsByName = new HashMap<>();
    private final Map<SymbolNamespace, Map<Integer, List<Symbol>>> symbolsById = new HashMap<>();

    public OpenRuneSymbolProvider(Path projectRoot) {
        this(projectRoot,
                Objects.requireNonNull(projectRoot, "projectRoot").resolve(".data").resolve("gamevals"),
                List.of(projectRoot.resolve("gamevals.toml")));
    }

    public OpenRuneSymbolProvider(ServerProjectInspection inspection) {
        this(Objects.requireNonNull(inspection, "inspection").connection().root(),
                inspection.path(ServerPathKey.GAMEVALS)
                        .orElse(inspection.connection().root().resolve(".data").resolve("gamevals")),
                inspection.content().stream()
                        .filter(entry -> entry.kind() == ServerContentKind.GAMEVAL)
                        .map(entry -> entry.path())
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase("gamevals.toml"))
                        .distinct()
                        .toList());
    }

    private OpenRuneSymbolProvider(Path projectRoot, Path gamevalsDir, List<Path> authoredGamevals) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        for (SymbolNamespace ns : SymbolNamespace.values()) {
            symbolsByName.put(ns, new HashMap<>());
            symbolsById.put(ns, new HashMap<>());
        }
        indexProject(gamevalsDir, authoredGamevals);
    }

    private void indexProject(Path gamevalsDir, List<Path> authoredGamevals) {
        if (Files.isDirectory(gamevalsDir)) {
            indexRscmFile(gamevalsDir.resolve("loc.rscm"), SymbolNamespace.LOC);
            indexRscmFile(gamevalsDir.resolve("npc.rscm"), SymbolNamespace.NPC);
            indexRscmFile(gamevalsDir.resolve("obj.rscm"), SymbolNamespace.ITEM);
            indexRscmFile(gamevalsDir.resolve("item.rscm"), SymbolNamespace.ITEM);
            indexRscmFile(gamevalsDir.resolve("varbit.rscm"), SymbolNamespace.VARBIT);
            indexRscmFile(gamevalsDir.resolve("varp.rscm"), SymbolNamespace.VARP);
            indexRscmFile(gamevalsDir.resolve("varc.rscm"), SymbolNamespace.VARC);
            indexRscmFile(gamevalsDir.resolve("interface.rscm"), SymbolNamespace.INTERFACE);
            indexRscmFile(gamevalsDir.resolve("component.rscm"), SymbolNamespace.COMPONENT);
            indexRscmFile(gamevalsDir.resolve("clientscript.rscm"), SymbolNamespace.CLIENTSCRIPT);
            indexRscmFile(gamevalsDir.resolve("dbtable.rscm"), SymbolNamespace.DB_TABLE);
            indexRscmFile(gamevalsDir.resolve("area.rscm"), SymbolNamespace.AREA);
            indexRscmFile(gamevalsDir.resolve("seq.rscm"), SymbolNamespace.SEQUENCE);
            indexRscmFile(gamevalsDir.resolve("spotanim.rscm"), SymbolNamespace.SPOTANIM);
        }

        for (Path tomlFile : authoredGamevals) {
            if (Files.isRegularFile(tomlFile)) {
                indexTomlFile(tomlFile);
            }
        }
    }

    private void indexRscmFile(Path file, SymbolNamespace namespace) {
        if (!Files.isRegularFile(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseAndAdd(line, namespace, file.toString());
            }
        } catch (IOException ignored) {
        }
    }

    private void indexTomlFile(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            SymbolNamespace currentNs = null;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    String section = trimmed.substring(1, trimmed.length() - 1)
                            .toLowerCase(Locale.ROOT);
                    currentNs = namespaceForGamevalSection(section);
                } else if (currentNs != null && trimmed.contains("=")) {
                    parseAndAdd(trimmed, currentNs, file.toString());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static SymbolNamespace namespaceForGamevalSection(String section) {
        String normalized = section.startsWith("gamevals.")
                ? section.substring("gamevals.".length()) : section;
        return switch (normalized) {
            case "loc" -> SymbolNamespace.LOC;
            case "npc" -> SymbolNamespace.NPC;
            case "obj", "item" -> SymbolNamespace.ITEM;
            case "varbit" -> SymbolNamespace.VARBIT;
            case "varp" -> SymbolNamespace.VARP;
            case "varc", "varcon" -> SymbolNamespace.VARC;
            case "interface" -> SymbolNamespace.INTERFACE;
            case "component" -> SymbolNamespace.COMPONENT;
            case "clientscript" -> SymbolNamespace.CLIENTSCRIPT;
            case "dbtable" -> SymbolNamespace.DB_TABLE;
            case "area" -> SymbolNamespace.AREA;
            case "seq" -> SymbolNamespace.SEQUENCE;
            case "spotanim" -> SymbolNamespace.SPOTANIM;
            default -> null;
        };
    }

    private void parseAndAdd(String line, SymbolNamespace namespace, String sourceFile) {
        String clean = line.trim();
        if (clean.isEmpty() || clean.startsWith("#") || clean.startsWith("//")) return;

        int eq = clean.indexOf('=');
        if (eq == -1) eq = clean.indexOf(':');
        if (eq == -1) return;

        String key = clean.substring(0, eq).trim();
        String valStr = clean.substring(eq + 1).trim();

        try {
            int id = Integer.parseInt(valStr);
            Symbol symbol = Symbol.of(namespace, key, id, "OpenRune Project", sourceFile);
            symbolsByName.get(namespace).put(key.toLowerCase(Locale.ROOT), symbol);
            symbolsById.get(namespace).computeIfAbsent(id, k -> new ArrayList<>()).add(symbol);
        } catch (NumberFormatException ignored) {
        }
    }

    /** Manually registers a symbol in this provider (useful for testing or dynamic addition). */
    public void addSymbol(Symbol symbol) {
        Objects.requireNonNull(symbol, "symbol");
        symbolsByName.get(symbol.namespace()).put(symbol.bareName().toLowerCase(Locale.ROOT), symbol);
        symbolsById.get(symbol.namespace()).computeIfAbsent(symbol.id(), k -> new ArrayList<>()).add(symbol);
    }

    @Override
    public String id() {
        return "openrune.symbols";
    }

    @Override
    public String name() {
        return "OpenRune GameVals & RSCM";
    }

    @Override
    public Optional<Symbol> resolve(SymbolNamespace namespace, String name) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
        return Optional.ofNullable(symbolsByName.get(namespace).get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<Symbol> reverse(SymbolNamespace namespace, int id) {
        Objects.requireNonNull(namespace, "namespace");
        return symbolsById.get(namespace).getOrDefault(id, List.of());
    }

    @Override
    public List<Symbol> search(SymbolNamespace namespace, String query) {
        Objects.requireNonNull(namespace, "namespace");
        String lower = query.toLowerCase(Locale.ROOT);
        List<Symbol> results = new ArrayList<>();
        for (Symbol s : symbolsByName.get(namespace).values()) {
            if (s.bareName().contains(lower)) {
                results.add(s);
            }
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public List<Symbol> all(SymbolNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return List.copyOf(symbolsByName.get(namespace).values());
    }
}
