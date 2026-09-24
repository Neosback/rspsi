package com.rspsi.editor.inspector;

import com.rspsi.editor.integration.semantic.SemanticContentEdge;
import com.rspsi.editor.integration.semantic.SemanticContentGraph;
import com.rspsi.editor.integration.semantic.SemanticContentNode;
import com.rspsi.editor.integration.semantic.SemanticContentNodeKind;
import com.rspsi.editor.integration.semantic.SemanticRelationKind;
import com.rspsi.editor.model.WorldObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectContentFacetResolverTest {
    @Test
    void resolvesSelectedObjectIntoServerContentFacet() {
        var object = node("object:1234", SemanticContentNodeKind.OBJECT_DEFINITION,
                "loc.coal_rock", "loc.coal_rock",
                Map.of(
                        "objectSymbol", "loc.coal_rock",
                        "sourcePath", "/project/content/mining.toml",
                        "writableSource", "true",
                        "numericId", "1234"));
        var loc = node("symbol:loc.coal_rock", SemanticContentNodeKind.SYMBOL,
                "loc.coal_rock", "loc.coal_rock", Map.of());
        var inherit = node("symbol:loc.base_rock", SemanticContentNodeKind.SYMBOL,
                "loc.base_rock", "loc.base_rock", Map.of());
        var group = node("symbol:content.rock", SemanticContentNodeKind.SYMBOL,
                "content.rock", "content.rock", Map.of());
        var param = node("symbol:param.next_loc_stage", SemanticContentNodeKind.SYMBOL,
                "param.next_loc_stage", "param.next_loc_stage", Map.of());
        var handler = node("handler:mining", SemanticContentNodeKind.HANDLER,
                "onOpContentLoc1", "onOpContentLoc1", Map.of());

        var graph = new SemanticContentGraph(
                List.of(object, loc, inherit, group, param, handler),
                List.of(
                        edge("id", object.id(), loc.id(), SemanticRelationKind.IDENTIFIED_BY, Map.of()),
                        edge("inherit", object.id(), inherit.id(), SemanticRelationKind.INHERITS, Map.of()),
                        edge("group", object.id(), group.id(), SemanticRelationKind.CONTENT_GROUP, Map.of()),
                        edge("param", object.id(), param.id(), SemanticRelationKind.HAS_PARAM,
                                Map.of("value", "loc.depleted_rock")),
                        edge("handler", handler.id(), group.id(), SemanticRelationKind.TARGETS, Map.of())),
                List.of());

        var selected = new WorldObject(1234, 10, 0, 0, 10, 10);
        var facet = new ObjectContentFacetResolver().resolve(selected, graph).orElseThrow();

        assertEquals(1234, facet.objectId());
        assertEquals("loc.coal_rock", facet.objectSymbol());
        assertEquals("loc.base_rock", facet.inheritSymbol().orElseThrow());
        assertEquals("content.rock", facet.contentGroup().orElseThrow());
        assertEquals(List.of("onOpContentLoc1"), facet.handlers());
        assertEquals("loc.depleted_rock", facet.params().get("param.next_loc_stage"));
        assertEquals("/project/content/mining.toml", facet.authoredSource().orElseThrow());
        assertTrue(facet.authoredSourceWritable());
        assertFalse(facet.editLensAvailable());
    }

    @Test
    void remainsAbsentWhenSelectedObjectHasNoServerOverlay() {
        var graph = new SemanticContentGraph(List.of(), List.of(), List.of());
        var selected = new WorldObject(9999, 10, 0, 0, 10, 10);

        assertTrue(new ObjectContentFacetResolver().resolve(selected, graph).isEmpty());
    }

    private static SemanticContentNode node(
            String id,
            SemanticContentNodeKind kind,
            String key,
            String label,
            Map<String, String> attributes) {
        return new SemanticContentNode(id, kind, key, label, attributes, List.of());
    }

    private static SemanticContentEdge edge(
            String id,
            String from,
            String to,
            SemanticRelationKind relation,
            Map<String, String> attributes) {
        return new SemanticContentEdge(id, from, to, relation, 1.0f, attributes, List.of());
    }
}
