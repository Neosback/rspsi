package com.rspsi.editor.integration.semantic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable project semantic graph assembled from independently-provenanced sources. */
public final class SemanticContentGraph {
    private final Map<String, SemanticContentNode> nodes;
    private final List<SemanticContentEdge> edges;
    private final Map<String, List<SemanticContentEdge>> outgoing;
    private final Map<String, List<SemanticContentEdge>> incoming;
    private final Map<SemanticContentNodeKind, List<SemanticContentNode>> byKind;
    private final List<String> diagnostics;

    public SemanticContentGraph(
            List<SemanticContentNode> nodes,
            List<SemanticContentEdge> edges,
            List<String> diagnostics) {
        LinkedHashMap<String, SemanticContentNode> nodeMap = new LinkedHashMap<>();
        for (SemanticContentNode node : nodes == null ? List.<SemanticContentNode>of() : nodes) {
            SemanticContentNode previous = nodeMap.putIfAbsent(node.id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate semantic node id: " + node.id());
            }
        }

        List<SemanticContentEdge> edgeList = List.copyOf(edges == null ? List.of() : edges);
        Map<String, List<SemanticContentEdge>> outgoingIndex = new LinkedHashMap<>();
        Map<String, List<SemanticContentEdge>> incomingIndex = new LinkedHashMap<>();
        for (SemanticContentEdge edge : edgeList) {
            if (!nodeMap.containsKey(edge.from()) || !nodeMap.containsKey(edge.to())) {
                throw new IllegalArgumentException("semantic edge references missing node: " + edge.id());
            }
            outgoingIndex.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incomingIndex.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
        }

        EnumMap<SemanticContentNodeKind, List<SemanticContentNode>> kindIndex =
                new EnumMap<>(SemanticContentNodeKind.class);
        for (SemanticContentNode node : nodeMap.values()) {
            kindIndex.computeIfAbsent(node.kind(), ignored -> new ArrayList<>()).add(node);
        }

        this.nodes = Map.copyOf(nodeMap);
        this.edges = edgeList;
        this.outgoing = freeze(outgoingIndex);
        this.incoming = freeze(incomingIndex);

        EnumMap<SemanticContentNodeKind, List<SemanticContentNode>> frozenKinds =
                new EnumMap<>(SemanticContentNodeKind.class);
        kindIndex.forEach((kind, values) -> frozenKinds.put(kind, List.copyOf(values)));
        this.byKind = Map.copyOf(frozenKinds);
        this.diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public List<SemanticContentNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<SemanticContentEdge> edges() {
        return edges;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public Optional<SemanticContentNode> node(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(nodes.get(id));
    }

    public Optional<SemanticContentNode> symbol(String qualifiedName) {
        String canonical = SemanticSymbolNames.canonical(qualifiedName);
        if (canonical.isBlank()) return Optional.empty();
        return node(SemanticSymbolNames.nodeId(canonical));
    }

    public List<SemanticContentNode> nodes(SemanticContentNodeKind kind) {
        return byKind.getOrDefault(Objects.requireNonNull(kind, "kind"), List.of());
    }

    public List<SemanticContentEdge> outgoing(String nodeId) {
        return outgoing.getOrDefault(nodeId, List.of());
    }

    public List<SemanticContentEdge> incoming(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of());
    }

    public List<SemanticContentEdge> outgoing(String nodeId, SemanticRelationKind relation) {
        Objects.requireNonNull(relation, "relation");
        return outgoing(nodeId).stream().filter(edge -> edge.relation() == relation).toList();
    }

    public List<SemanticContentEdge> incoming(String nodeId, SemanticRelationKind relation) {
        Objects.requireNonNull(relation, "relation");
        return incoming(nodeId).stream().filter(edge -> edge.relation() == relation).toList();
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> source) {
        LinkedHashMap<String, List<T>> result = new LinkedHashMap<>();
        source.forEach((key, values) -> result.put(key, List.copyOf(values)));
        return Map.copyOf(result);
    }
}
