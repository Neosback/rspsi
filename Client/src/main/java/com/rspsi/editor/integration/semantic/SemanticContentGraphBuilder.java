package com.rspsi.editor.integration.semantic;

import com.rspsi.editor.integration.reference.ContentReference;
import com.rspsi.editor.integration.reference.ReferenceProvider;
import com.rspsi.editor.symbols.Symbol;
import com.rspsi.editor.symbols.SymbolNamespace;
import com.rspsi.editor.symbols.SymbolProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Joins independently-provenanced source, GameVal/RSCM and declarative-reference indexes into one
 * read-only semantic graph.
 *
 * <p>The builder does not infer runtime behavior or editability. It only creates relationships
 * supported by source/index evidence.</p>
 */
public final class SemanticContentGraphBuilder {

    public SemanticContentGraph build(
            Path projectRoot,
            SemanticSourceIndex sourceIndex,
            SymbolProvider symbolProvider,
            ReferenceProvider referenceProvider) {
        Path root = Objects.requireNonNull(projectRoot, "projectRoot")
                .toAbsolutePath().normalize();
        State state = new State(root);

        if (sourceIndex != null) {
            state.diagnostics.addAll(sourceIndex.diagnostics());
        }
        if (symbolProvider != null) {
            ingestSymbols(state, symbolProvider);
        }
        if (sourceIndex != null) {
            ingestSource(state, sourceIndex);
        }
        if (referenceProvider != null) {
            ingestDeclarativeReferences(state, referenceProvider);
        }

        return state.freeze();
    }

    private static void ingestSymbols(State state, SymbolProvider provider) {
        for (SymbolNamespace namespace : SymbolNamespace.values()) {
            for (Symbol symbol : provider.all(namespace)) {
                String canonical = SemanticSymbolNames.canonical(symbol.qualifiedName());
                if (canonical.isBlank()) continue;

                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("namespace", SemanticSymbolNames.namespace(canonical));
                attributes.put("symbolNamespace", symbol.namespace().name());
                attributes.put("resolved", "true");
                attributes.put("numericId", Integer.toString(symbol.id()));
                attributes.put("symbolSource", symbol.source());

                SemanticEvidence evidence = new SemanticEvidence(
                        SemanticEvidenceKind.SYMBOL_MAPPING,
                        provider.id(),
                        java.util.Optional.empty(),
                        symbol.filePath(),
                        0,
                        symbol.confidence(),
                        Map.of(
                                "symbolSource", symbol.source(),
                                "numericId", Integer.toString(symbol.id())));

                MutableNode node = state.ensureSymbol(canonical, symbol.qualifiedName(), evidence);
                state.mergeAttributes(node, attributes);
                node.aliases.add(symbol.qualifiedName());
                if (symbol.namespace() == SymbolNamespace.ITEM) {
                    node.aliases.add("obj." + symbol.bareName());
                }
            }
        }
    }

    private static void ingestSource(State state, SemanticSourceIndex index) {
        for (Path file : index.files()) {
            state.ensureResource(file, "kotlin", Map.of());
        }

        List<ScriptRef> scripts = new ArrayList<>();
        for (SemanticSourceFact fact : index.facts()) {
            if (fact.kind() != SemanticFactKind.PLUGIN_SCRIPT
                    && fact.kind() != SemanticFactKind.QUEST_SCRIPT) {
                continue;
            }
            boolean quest = fact.kind() == SemanticFactKind.QUEST_SCRIPT;
            String pkg = fact.attributes().getOrDefault("package", "").trim();
            String qualifiedName = pkg.isBlank() ? fact.name() : pkg + "." + fact.name();
            String id = (quest ? "quest:" : "script:") + qualifiedName.toLowerCase(Locale.ROOT);

            MutableNode script = state.ensureNode(
                    id,
                    quest ? SemanticContentNodeKind.QUEST : SemanticContentNodeKind.SCRIPT,
                    qualifiedName,
                    fact.name());
            state.mergeAttributes(script, fact.attributes());
            script.attributes.putIfAbsent("qualifiedName", qualifiedName);
            script.evidence.add(SemanticEvidence.source(fact));

            MutableNode resource = state.ensureResource(fact.source().file(), "kotlin", fact.attributes());
            state.ensureEdge(
                    id, resource.id, SemanticRelationKind.DECLARED_IN,
                    fact.confidence(), Map.of(), SemanticEvidence.source(fact));

            scripts.add(new ScriptRef(
                    fact.source().file().toAbsolutePath().normalize(),
                    qualifiedName,
                    id,
                    quest));
        }

        scripts.sort(Comparator.comparingInt((ScriptRef ref) -> ref.qualifiedName().length()).reversed());

        for (SemanticSourceFact fact : index.facts()) {
            switch (fact.kind()) {
                case QUEST_DEFINITION -> ingestQuestDefinition(state, scripts, fact);
                case SCRIPT_HANDLER -> ingestHandler(state, scripts, fact);
                case VAR_BINDING -> ingestVarBinding(state, scripts, fact);
                case SYMBOL_REFERENCE -> ingestSourceReference(state, scripts, fact);
                default -> {
                    // Declarations and generic calls remain available in SemanticSourceIndex.
                    // The content graph promotes only relationships with a stable domain meaning.
                }
            }
        }
    }

    private static void ingestQuestDefinition(
            State state,
            List<ScriptRef> scripts,
            SemanticSourceFact fact) {
        ScriptRef quest = findScriptByClass(scripts, fact, true);
        if (quest == null) return;

        MutableNode node = state.nodes.get(quest.id());
        state.mergeAttributes(node, fact.attributes());
        node.evidence.add(SemanticEvidence.source(fact));

        String questVar = fact.attributes().getOrDefault("questVar", "");
        if (SemanticSymbolNames.looksQualified(questVar)) {
            MutableNode symbol = state.ensureSymbol(
                    questVar, questVar, SemanticEvidence.source(fact));
            state.ensureEdge(
                    node.id, symbol.id, SemanticRelationKind.USES_STATE,
                    fact.confidence(),
                    Map.of("role", "quest-state"),
                    SemanticEvidence.source(fact));
        }
    }

    private static void ingestHandler(
            State state,
            List<ScriptRef> scripts,
            SemanticSourceFact fact) {
        String relative = state.relative(fact.source().file());
        String handlerId = "handler:" + relative.toLowerCase(Locale.ROOT)
                + ":" + fact.source().startOffset();

        MutableNode handler = state.ensureNode(
                handlerId,
                SemanticContentNodeKind.HANDLER,
                fact.name() + "@" + relative + ":" + fact.source().startLine(),
                fact.name());
        state.mergeAttributes(handler, fact.attributes());
        handler.attributes.putIfAbsent("owner", fact.owner());
        if (!fact.arguments().isEmpty()) {
            handler.attributes.putIfAbsent("arguments", String.join(" | ", fact.arguments()));
        }
        handler.evidence.add(SemanticEvidence.source(fact));

        MutableNode resource = state.ensureResource(fact.source().file(), "kotlin", fact.attributes());
        state.ensureEdge(
                handler.id, resource.id, SemanticRelationKind.DECLARED_IN,
                fact.confidence(), Map.of(), SemanticEvidence.source(fact));

        MutableNode owner = ownerNode(state, scripts, fact);
        if (!owner.id.equals(resource.id)) {
            state.ensureEdge(
                    owner.id, handler.id, SemanticRelationKind.OWNS,
                    fact.confidence(), Map.of(), SemanticEvidence.source(fact));
        }

        for (String argument : fact.arguments()) {
            if (!SemanticSymbolNames.looksQualified(argument)) continue;
            MutableNode symbol = state.ensureSymbol(argument, argument, SemanticEvidence.source(fact));
            state.ensureEdge(
                    handler.id, symbol.id, SemanticRelationKind.TARGETS,
                    fact.confidence(),
                    Map.of("handler", fact.name()),
                    SemanticEvidence.source(fact));
        }
    }

    private static void ingestVarBinding(
            State state,
            List<ScriptRef> scripts,
            SemanticSourceFact fact) {
        MutableNode owner = ownerNode(state, scripts, fact);
        for (String argument : fact.arguments()) {
            if (!SemanticSymbolNames.looksQualified(argument)) continue;
            String namespace = SemanticSymbolNames.namespace(argument);
            if (!(namespace.equals("varbit") || namespace.equals("varp")
                    || namespace.equals("varc"))) {
                continue;
            }
            MutableNode symbol = state.ensureSymbol(argument, argument, SemanticEvidence.source(fact));
            state.ensureEdge(
                    owner.id, symbol.id, SemanticRelationKind.BINDS_STATE,
                    fact.confidence(),
                    Map.of("binding", fact.name()),
                    SemanticEvidence.source(fact));
        }
    }

    private static void ingestSourceReference(
            State state,
            List<ScriptRef> scripts,
            SemanticSourceFact fact) {
        if (!SemanticSymbolNames.looksQualified(fact.name())) return;
        MutableNode owner = ownerNode(state, scripts, fact);
        MutableNode symbol = state.ensureSymbol(fact.name(), fact.name(), SemanticEvidence.source(fact));
        state.ensureEdge(
                owner.id, symbol.id, SemanticRelationKind.REFERENCES,
                fact.confidence(), Map.of(), SemanticEvidence.source(fact));
    }

    private static void ingestDeclarativeReferences(State state, ReferenceProvider provider) {
        for (ContentReference reference : provider.allReferences()) {
            Path file = state.resolve(reference.relativePath());
            MutableNode resource = state.ensureResource(
                    file,
                    "declarative",
                    Map.of(
                            "module", reference.module(),
                            "contextType", reference.contextType()));

            String target = reference.targetSymbol();
            if (target == null || target.isBlank()) continue;

            SemanticEvidence evidence = new SemanticEvidence(
                    SemanticEvidenceKind.DECLARATIVE_REFERENCE,
                    provider.id(),
                    java.util.Optional.empty(),
                    reference.relativePath(),
                    Math.max(0, reference.lineNumber()),
                    1.0f,
                    Map.of(
                            "module", reference.module(),
                            "contextType", reference.contextType(),
                            "snippet", reference.snippet()));

            MutableNode symbol = state.ensureSymbol(target, target, evidence);
            if (reference.id() >= 0) {
                state.mergeAttributes(symbol, Map.of(
                        "resolved", "true",
                        "numericId", Integer.toString(reference.id())));
            }
            state.ensureEdge(
                    resource.id, symbol.id, SemanticRelationKind.REFERENCES,
                    1.0f,
                    Map.of("contextType", reference.contextType()),
                    evidence);
        }
    }

    private static MutableNode ownerNode(
            State state,
            List<ScriptRef> scripts,
            SemanticSourceFact fact) {
        Path file = fact.source().file().toAbsolutePath().normalize();
        for (ScriptRef script : scripts) {
            if (!script.file().equals(file)) continue;
            String owner = fact.owner();
            if (owner.equals(script.qualifiedName())
                    || owner.startsWith(script.qualifiedName() + ".")) {
                return state.nodes.get(script.id());
            }
        }
        return state.ensureResource(file, "kotlin", fact.attributes());
    }

    private static ScriptRef findScriptByClass(
            List<ScriptRef> scripts,
            SemanticSourceFact fact,
            boolean questOnly) {
        Path file = fact.source().file().toAbsolutePath().normalize();
        String pkg = fact.attributes().getOrDefault("package", "").trim();
        String qualified = pkg.isBlank() ? fact.name() : pkg + "." + fact.name();
        for (ScriptRef script : scripts) {
            if (!script.file().equals(file)) continue;
            if (questOnly && !script.quest()) continue;
            if (script.qualifiedName().equals(qualified)) return script;
        }
        return null;
    }

    private record ScriptRef(Path file, String qualifiedName, String id, boolean quest) {
    }

    private static final class State {
        private final Path root;
        private final Map<String, MutableNode> nodes = new LinkedHashMap<>();
        private final Map<String, MutableEdge> edges = new LinkedHashMap<>();
        private final List<String> diagnostics = new ArrayList<>();

        private State(Path root) {
            this.root = root;
        }

        private MutableNode ensureNode(
                String id,
                SemanticContentNodeKind kind,
                String key,
                String label) {
            MutableNode existing = nodes.get(id);
            if (existing != null) {
                if (existing.kind != kind) {
                    diagnostics.add("Semantic node kind conflict for " + id + ": "
                            + existing.kind + " vs " + kind);
                }
                return existing;
            }
            MutableNode created = new MutableNode(id, kind, key, label);
            nodes.put(id, created);
            return created;
        }

        private MutableNode ensureResource(
                Path file,
                String resourceType,
                Map<String, String> attributes) {
            Path normalized = file.toAbsolutePath().normalize();
            String relative = relative(normalized);
            String id = "resource:" + relative.toLowerCase(Locale.ROOT);
            MutableNode resource = ensureNode(
                    id,
                    SemanticContentNodeKind.RESOURCE,
                    relative,
                    normalized.getFileName() == null ? relative : normalized.getFileName().toString());
            resource.attributes.putIfAbsent("path", normalized.toString());
            resource.attributes.putIfAbsent("resourceType", resourceType);
            mergeAttributes(resource, attributes);
            return resource;
        }

        private MutableNode ensureSymbol(
                String rawSymbol,
                String label,
                SemanticEvidence evidence) {
            String canonical = SemanticSymbolNames.canonical(rawSymbol);
            MutableNode symbol = ensureNode(
                    SemanticSymbolNames.nodeId(canonical),
                    SemanticContentNodeKind.SYMBOL,
                    canonical,
                    label == null || label.isBlank() ? canonical : label);
            symbol.attributes.putIfAbsent("namespace", SemanticSymbolNames.namespace(canonical));
            symbol.attributes.putIfAbsent("resolved", "false");
            symbol.aliases.add(rawSymbol);
            symbol.aliases.add(canonical);
            if (canonical.startsWith("item.")) {
                symbol.aliases.add("obj." + canonical.substring("item.".length()));
            }
            if (canonical.startsWith("varc.")) {
                symbol.aliases.add("varcon." + canonical.substring("varc.".length()));
            }
            if (evidence != null && !symbol.evidence.contains(evidence)) {
                symbol.evidence.add(evidence);
            }
            return symbol;
        }

        private void mergeAttributes(MutableNode node, Map<String, String> attributes) {
            attributes.forEach((key, value) -> {
                if (value == null || value.isBlank()) return;
                String existing = node.attributes.get(key);
                if (existing == null || existing.isBlank()
                        || (key.equals("resolved") && existing.equals("false") && value.equals("true"))) {
                    node.attributes.put(key, value);
                } else if (!existing.equals(value)) {
                    diagnostics.add("Semantic attribute conflict on " + node.id
                            + " for " + key + ": " + existing + " vs " + value);
                }
            });
        }

        private void ensureEdge(
                String from,
                String to,
                SemanticRelationKind relation,
                float confidence,
                Map<String, String> attributes,
                SemanticEvidence evidence) {
            String key = relation.name() + "\u0000" + from + "\u0000" + to;
            MutableEdge edge = edges.computeIfAbsent(
                    key, ignored -> new MutableEdge(from, to, relation));
            edge.confidence = Math.max(edge.confidence, confidence);
            attributes.forEach((name, value) -> edge.attributes.putIfAbsent(name, value));
            if (evidence != null && !edge.evidence.contains(evidence)) edge.evidence.add(evidence);
        }

        private Path resolve(String path) {
            if (path == null || path.isBlank()) return root.resolve("<unknown>").normalize();
            Path value = Path.of(path);
            return value.isAbsolute() ? value.normalize() : root.resolve(value).normalize();
        }

        private String relative(Path path) {
            Path normalized = path.toAbsolutePath().normalize();
            try {
                if (normalized.startsWith(root)) {
                    String result = root.relativize(normalized).toString().replace('\\', '/');
                    return result.isBlank() ? "." : result;
                }
            } catch (IllegalArgumentException ignored) {
            }
            return normalized.toString().replace('\\', '/');
        }

        private SemanticContentGraph freeze() {
            List<SemanticContentNode> frozenNodes = nodes.values().stream()
                    .map(MutableNode::freeze)
                    .toList();
            List<SemanticContentEdge> frozenEdges = edges.values().stream()
                    .map(MutableEdge::freeze)
                    .toList();
            return new SemanticContentGraph(frozenNodes, frozenEdges, diagnostics);
        }
    }

    private static final class MutableNode {
        private final String id;
        private final SemanticContentNodeKind kind;
        private final String key;
        private final String label;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final Set<String> aliases = new LinkedHashSet<>();
        private final List<SemanticEvidence> evidence = new ArrayList<>();

        private MutableNode(
                String id,
                SemanticContentNodeKind kind,
                String key,
                String label) {
            this.id = id;
            this.kind = kind;
            this.key = key;
            this.label = label;
        }

        private SemanticContentNode freeze() {
            if (!aliases.isEmpty()) {
                attributes.put("aliases", String.join(" | ", aliases));
            }
            return new SemanticContentNode(id, kind, key, label, attributes, evidence);
        }
    }

    private static final class MutableEdge {
        private final String from;
        private final String to;
        private final SemanticRelationKind relation;
        private float confidence;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final List<SemanticEvidence> evidence = new ArrayList<>();

        private MutableEdge(String from, String to, SemanticRelationKind relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }

        private SemanticContentEdge freeze() {
            String id = relation.name().toLowerCase(Locale.ROOT) + ":" + from + "->" + to;
            return new SemanticContentEdge(
                    id, from, to, relation, confidence, attributes, evidence);
        }
    }
}
