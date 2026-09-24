package com.rspsi.editor.plugin.builtin;

import com.rspsi.editor.inspector.ObjectContentFacetResolver;
import com.rspsi.editor.plugin.EditorInspector;
import com.rspsi.editor.plugin.EditorInspectorField;
import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.selection.ObjectSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** First-party inspector projection for OpenRune server/content semantics on a selected object. */
final class ObjectContentInspector implements EditorInspector {
    private final ObjectContentFacetResolver resolver = new ObjectContentFacetResolver();

    @Override
    public List<EditorInspectorField> inspect(EditorPluginContext context) {
        if (!(context.session().selection().current() instanceof ObjectSelection selection)) {
            return List.of();
        }
        var graph = context.integrations().activeSemanticContentGraph();
        if (graph.isEmpty()) return List.of();

        var facet = resolver.resolve(selection.object(), graph.orElseThrow());
        if (facet.isEmpty()) return List.of();
        var value = facet.orElseThrow();

        List<EditorInspectorField> fields = new ArrayList<>();
        fields.add(new EditorInspectorField(
                "object.content.symbol", "Server symbol", value.objectSymbol()));
        value.contentGroup().ifPresent(group -> fields.add(new EditorInspectorField(
                "object.content.group", "Content group", group)));
        value.inheritSymbol().ifPresent(inherit -> fields.add(new EditorInspectorField(
                "object.content.inherit", "Inherits", inherit)));

        if (!value.handlers().isEmpty()) {
            fields.add(new EditorInspectorField(
                    "object.content.handlers", "Handlers", String.join(", ", value.handlers())));
        }
        value.params().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> fields.add(new EditorInspectorField(
                        "object.content.param." + safeId(entry.getKey()),
                        entry.getKey(),
                        entry.getValue())));

        value.authoredSource().ifPresent(source -> fields.add(new EditorInspectorField(
                "object.content.source", "Authored source", source)));
        fields.add(new EditorInspectorField(
                "object.content.sourceWritable",
                "Authored source",
                value.authoredSourceWritable() ? "Source-controlled" : "Read-only / generated"));
        fields.add(new EditorInspectorField(
                "object.content.editability",
                "Editing",
                value.editLensAvailable()
                        ? "Verified edit lens available"
                        : "Read-only semantic view"));

        return List.copyOf(fields);
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
