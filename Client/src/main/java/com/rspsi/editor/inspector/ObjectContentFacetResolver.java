package com.rspsi.editor.inspector;

import com.rspsi.editor.integration.semantic.SemanticContentGraph;
import com.rspsi.editor.integration.semantic.SemanticContentNode;
import com.rspsi.editor.integration.semantic.SemanticContentNodeKind;
import com.rspsi.editor.integration.semantic.SemanticRelationKind;
import com.rspsi.editor.model.WorldObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Resolves a selected map object into its server/content semantic graph projection. */
public final class ObjectContentFacetResolver {

    public Optional<ObjectContentFacet> resolve(WorldObject object, SemanticContentGraph graph) {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(graph, "graph");

        Optional<SemanticContentNode> objectNode = graph.objectDefinition(object.id());
        if (objectNode.isEmpty()) return Optional.empty();
        SemanticContentNode node = objectNode.orElseThrow();

        String objectSymbol = firstTarget(graph, node.id(), SemanticRelationKind.IDENTIFIED_BY)
                .map(SemanticContentNode::key)
                .orElse(node.attributes().getOrDefault("objectSymbol", node.key()));
        Optional<String> inherit = firstTarget(graph, node.id(), SemanticRelationKind.INHERITS)
                .map(SemanticContentNode::key);
        Optional<SemanticContentNode> contentGroupNode =
                firstTarget(graph, node.id(), SemanticRelationKind.CONTENT_GROUP);
        Optional<String> contentGroup = contentGroupNode.map(SemanticContentNode::key);

        Set<String> handlers = new LinkedHashSet<>();
        contentGroupNode.ifPresent(group -> graph.incoming(
                        group.id(), SemanticRelationKind.TARGETS).stream()
                .map(edge -> graph.node(edge.from()).orElse(null))
                .filter(Objects::nonNull)
                .filter(handler -> handler.kind() == SemanticContentNodeKind.HANDLER)
                .map(SemanticContentNode::label)
                .forEach(handlers::add));

        Map<String, String> params = new LinkedHashMap<>();
        graph.outgoing(node.id(), SemanticRelationKind.HAS_PARAM).forEach(edge -> {
            SemanticContentNode param = graph.node(edge.to()).orElse(null);
            if (param == null) return;
            params.put(param.key(), edge.attributes().getOrDefault("value", ""));
        });

        String source = node.attributes().getOrDefault("sourcePath", "").trim();
        boolean writableSource = Boolean.parseBoolean(
                node.attributes().getOrDefault("writableSource", "false"));

        // No mutation contract is implemented in this slice. Authored-source provenance alone
        // must never be interpreted as permission to rewrite the file.
        return Optional.of(new ObjectContentFacet(
                object.id(),
                objectSymbol,
                inherit,
                contentGroup,
                List.copyOf(handlers),
                params,
                source.isBlank() ? Optional.empty() : Optional.of(source),
                writableSource,
                false));
    }

    private static Optional<SemanticContentNode> firstTarget(
            SemanticContentGraph graph,
            String from,
            SemanticRelationKind relation) {
        return graph.outgoing(from, relation).stream()
                .map(edge -> graph.node(edge.to()).orElse(null))
                .filter(Objects::nonNull)
                .findFirst();
    }
}
