package com.rspsi.editor.knowledge.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Directed property graph representing topological, compositional, and semantic relationships
 * between regions, tiles, objects, models, textures, and tags.
 */
public final class AssetKnowledgeGraph {
    private final Map<String, GraphNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, List<GraphEdge>> outgoing = new ConcurrentHashMap<>();
    private final Map<String, List<GraphEdge>> incoming = new ConcurrentHashMap<>();

    public void addNode(GraphNode node) {
        Objects.requireNonNull(node, "node");
        nodes.put(node.id(), node);
    }

    public Optional<GraphNode> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public void addEdge(GraphEdge edge) {
        Objects.requireNonNull(edge, "edge");
        outgoing.computeIfAbsent(edge.sourceId(), k -> new ArrayList<>()).add(edge);
        incoming.computeIfAbsent(edge.targetId(), k -> new ArrayList<>()).add(edge);
    }

    public List<GraphEdge> outgoingEdges(String nodeId) {
        return outgoing.getOrDefault(nodeId, List.of());
    }

    public List<GraphEdge> incomingEdges(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of());
    }

    public Set<String> neighbors(String nodeId) {
        Set<String> set = new HashSet<>();
        for (GraphEdge edge : outgoingEdges(nodeId)) {
            set.add(edge.targetId());
        }
        for (GraphEdge edge : incomingEdges(nodeId)) {
            set.add(edge.sourceId());
        }
        return Collections.unmodifiableSet(set);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return outgoing.values().stream().mapToInt(List::size).sum();
    }
}
