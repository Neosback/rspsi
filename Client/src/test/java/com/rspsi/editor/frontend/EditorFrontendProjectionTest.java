package com.rspsi.editor.frontend;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.input.EditorInputRouter;
import com.rspsi.editor.model.WorldModel;
import com.rspsi.editor.plugin.EditorPluginHost;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.tool.EditorToolController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorFrontendProjectionTest {
    @Test
    void imguiProjectionReadsTheSameSessionAndSceneContracts() {
        EditorSession session = new EditorSession(new WorldModel(2, 2, 4));
        EditorSceneSnapshot snapshot = EditorSceneSnapshot.from(new RenderScene(session.world()));
        try (EditorPluginHost host = EditorPluginHost.initialize(
                java.util.List.of(), session, EmptyAssetRepository.INSTANCE, () -> snapshot)) {
            EditorToolController tools = new EditorToolController();
            DearImGuiFrontendAdapter adapter = new DearImGuiFrontendAdapter(
                    host.context(), new EditorInputRouter(host.context(), tools));

            EditorFrontendFrame frame = adapter.frame();

            assertEquals(snapshot, frame.scene());
            assertTrue(frame.editable());
            assertTrue(frame.canSave() == false);
            assertEquals(host.registry().toolIds(), frame.tools());
            assertEquals(host.registry().commandRegistrations().stream()
                    .map(value -> value.id()).toList(), frame.commands());
        }
    }
}
