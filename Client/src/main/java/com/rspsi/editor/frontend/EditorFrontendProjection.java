package com.rspsi.editor.frontend;

import com.rspsi.editor.plugin.EditorPluginContext;
import com.rspsi.editor.plugin.EditorPluginRegistry;
import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.plugin.EditorSceneAccess;

import java.util.Objects;

/** Builds a frontend projection without creating another editor state model. */
public final class EditorFrontendProjection {
    private EditorFrontendProjection() {
    }

    public static EditorFrontendFrame capture(EditorPluginContext context) {
        Objects.requireNonNull(context, "context");
        EditorSceneSnapshot scene = context.scene()
                .map(EditorSceneAccess::checkedSnapshot)
                .orElseThrow(() -> new IllegalStateException("Frontend projection requires a scene"));
        EditorPluginRegistry registry = context.registry();
        return new EditorFrontendFrame(
                scene,
                context.session().canEdit(),
                context.session().isDirty(),
                context.session().canSave(),
                context.session().history().canUndo(),
                context.session().history().canRedo(),
                context.session().selection().tiles(),
                registry.toolIds(),
                registry.commandRegistrations().stream().map(value -> value.id()).toList(),
                registry.toolContextRegistrations().stream().map(value -> value.id()).toList(),
                registry.panels().stream().map(value -> value.id()).toList(),
                registry.workspaces().stream().map(value -> value.id()).toList(),
                registry.inspectorRegistrations().stream().map(value -> value.id()).toList(),
                registry.overlayRegistrations().stream().map(value -> value.id()).toList(),
                registry.assetProviderRegistrations().stream().map(value -> value.id()).toList(),
                registry.validatorRegistrations().stream().map(value -> value.id()).toList(),
                registry.shortcutRegistrations().stream().map(value -> value.id()).toList());
    }
}
