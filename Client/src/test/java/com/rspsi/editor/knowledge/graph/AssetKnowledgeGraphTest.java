package com.rspsi.editor.knowledge.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetKnowledgeGraphTest {
    private AssetKnowledgeGraph graph;

    @BeforeEach
    void setUp() {
        graph = new AssetKnowledgeGraph();
    }

    @Test
    void addAndLookupNodes() {
        GraphNode objNode = GraphNode.of("obj:100", GraphNode.NodeType.OBJECT_DEFINITION, Map.of("name", "Tree"));
        graph.addNode(objNode);

        assertEquals(1, graph.nodeCount());
        assertTrue(graph.node("obj:100").isPresent());
        assertEquals("Tree", graph.node("obj:100").get().attributes().get("name"));
    }

    @Test
    void addEdgesAndQueryRelationships() {
        GraphNode tileNode = GraphNode.of("tile:0:10:10", GraphNode.NodeType.TILE);
        GraphNode objNode = GraphNode.of("obj:100", GraphNode.NodeType.OBJECT_DEFINITION);
        GraphNode modelNode = GraphNode.of("model:500", GraphNode.NodeType.MODEL);
        GraphNode tagNode = GraphNode.of("tag:core:TREE", GraphNode.NodeType.SEMANTIC_TAG);

        graph.addNode(tileNode);
        graph.addNode(objNode);
        graph.addNode(modelNode);
        graph.addNode(tagNode);

        // Tile -> CONTAINS -> Object
        graph.addEdge(GraphEdge.of("tile:0:10:10", "obj:100", GraphEdge.EdgeType.CONTAINS));
        // Object -> USES -> Model
        graph.addEdge(GraphEdge.of("obj:100", "model:500", GraphEdge.EdgeType.USES));
        // Object -> TAGGED_AS -> Tag
        graph.addEdge(GraphEdge.of("obj:100", "tag:core:TREE", GraphEdge.EdgeType.TAGGED_AS, 0.95f));

        assertEquals(4, graph.nodeCount());
        assertEquals(3, graph.edgeCount());

        // Test outgoing from Object
        List<GraphEdge> outgoing = graph.outgoingEdges("obj:100");
        assertEquals(2, outgoing.size());

        // Test incoming to Object
        List<GraphEdge> incoming = graph.incomingEdges("obj:100");
        assertEquals(1, incoming.size());
        assertEquals("tile:0:10:10", incoming.get(0).sourceId());

        // Test bidirectional neighbors of Object
        Set<String> neighbors = graph.neighbors("obj:100");
        assertEquals(3, neighbors.size());
        assertTrue(neighbors.contains("tile:0:10:10"));
        assertTrue(neighbors.contains("model:500"));
        assertTrue(neighbors.contains("tag:core:TREE"));
    }
}
