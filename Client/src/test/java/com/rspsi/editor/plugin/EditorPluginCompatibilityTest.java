package com.rspsi.editor.plugin;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.assets.EmptyAssetRepository;
import com.rspsi.editor.model.WorldModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPluginCompatibilityTest {
    @Test
    void incompatiblePluginApiIsRejectedBeforeInitialization() {
        EditorPlugin incompatible = new EditorPlugin() {
            @Override public String id() { return "test.future-api"; }

            @Override
            public EditorPluginDescriptor descriptor() {
                return new EditorPluginDescriptor(id(), "Future API", "1", 2, List.of());
            }

            @Override
            public void initialize(EditorPluginContext context) {
                throw new AssertionError("incompatible plugins must not initialize");
            }
        };

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                EditorPluginHost.initialize(List.of(incompatible),
                        new EditorSession(new WorldModel(1, 1, 1)),
                        EmptyAssetRepository.INSTANCE));
        assertTrue(failure.getMessage().contains("requires editor plugin API 2"));
    }
}
