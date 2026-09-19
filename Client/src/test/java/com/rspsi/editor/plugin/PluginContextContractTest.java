package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.AssetRepository;
import com.rspsi.editor.generation.GenerationSchema;
import com.rspsi.editor.generation.ProposedChanges;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginContextContractTest {

    @Test
    void pluginContextProvidesAllRequiredServices() {
        WorldDocument world = new WorldDocument(8, 8, 2);
        EditorSession session = new EditorSession(world);
        EditorPluginContext context = new EditorPluginContext(session, AssetRepository.empty());

        PluginContext pluginCtx = context;
        assertNotNull(pluginCtx.session());
        assertNotNull(pluginCtx.world());
        assertNotNull(pluginCtx.scene());
        assertNotNull(pluginCtx.selection());
        assertNotNull(pluginCtx.history());
        assertNotNull(pluginCtx.commands());
        assertNotNull(pluginCtx.settings());
        assertNotNull(pluginCtx.settingsService());
        assertNotNull(pluginCtx.assets());
        assertNotNull(pluginCtx.tasks());
        assertNotNull(pluginCtx.notifications());
        assertNotNull(pluginCtx.knowledge());
        assertNotNull(pluginCtx.generators());
        assertNotNull(pluginCtx.owner());
    }

    @Test
    void pluginApiRegistersAndCleansUpKnowledgeAndGenerators() {
        WorldDocument world = new WorldDocument(8, 8, 2);
        EditorSession session = new EditorSession(world);
        EditorPluginContext context = new EditorPluginContext(session, AssetRepository.empty());

        EditorPlugin testPlugin = new EditorPlugin() {
            @Override
            public String id() {
                return "sample-plugin";
            }

            @Override
            public EditorPluginDescriptor descriptor() {
                return new EditorPluginDescriptor("sample-plugin", "Sample Plugin",
                        "1.0.0", EditorPluginApi.CURRENT_VERSION, List.of());
            }

            @Override
            public void initialize(EditorPluginContext ctx) {
                PluginApi api = ctx.api(this);

                // Register semantic analyzer
                api.knowledgeAnalyzer((w, emitter) -> {
                    emitter.accept(new TileCoordinate(0, 1, 1), SemanticTag.of("SAMPLE_TAG"));
                });

                // Register procedural generator
                api.generator("sample-gen", GenerationSchema.TERRAIN, "Sample Gen", "Test",
                        (req, pCtx) -> ProposedChanges.builder().addDiagnostic("sample-ok").build());
            }
        };

        try (EditorPluginHost host = EditorPluginHost.initialize(List.of(testPlugin), session, AssetRepository.empty())) {
            // Verify contributions exist while host is active
            assertTrue(host.context().knowledge().snapshot().hasTag(new TileCoordinate(0, 1, 1), SemanticTag.of("SAMPLE_TAG")));
            assertTrue(host.context().generators().generator("sample-gen").isPresent());
        }

        // Host closed: verify all contributions are cleaned up
        assertFalse(context.knowledge().snapshot().hasTag(new TileCoordinate(0, 1, 1), SemanticTag.of("SAMPLE_TAG")));
        assertFalse(context.generators().generator("sample-gen").isPresent());
    }
}
