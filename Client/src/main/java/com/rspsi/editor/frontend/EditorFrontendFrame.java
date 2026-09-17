package com.rspsi.editor.frontend;

import com.rspsi.editor.plugin.EditorSceneSnapshot;
import com.rspsi.editor.model.TileCoordinate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable projection shared by JavaFX, Dear ImGui, and future frontends. */
public record EditorFrontendFrame(
        EditorSceneSnapshot scene,
        boolean editable,
        boolean dirty,
        boolean canSave,
        boolean canUndo,
        boolean canRedo,
        Set<TileCoordinate> selectedTiles,
        List<String> tools,
        List<String> commands,
        List<String> toolContexts,
        List<String> panels,
        List<String> workspaces,
        List<String> inspectors,
        List<String> overlays,
        List<String> assetProviders,
        List<String> validators,
        List<String> shortcuts) {
    public EditorFrontendFrame {
        scene = Objects.requireNonNull(scene, "scene");
        selectedTiles = Set.copyOf(selectedTiles == null ? Set.of() : selectedTiles);
        tools = List.copyOf(tools == null ? List.of() : tools);
        commands = List.copyOf(commands == null ? List.of() : commands);
        toolContexts = List.copyOf(toolContexts == null ? List.of() : toolContexts);
        panels = List.copyOf(panels == null ? List.of() : panels);
        workspaces = List.copyOf(workspaces == null ? List.of() : workspaces);
        inspectors = List.copyOf(inspectors == null ? List.of() : inspectors);
        overlays = List.copyOf(overlays == null ? List.of() : overlays);
        assetProviders = List.copyOf(assetProviders == null ? List.of() : assetProviders);
        validators = List.copyOf(validators == null ? List.of() : validators);
        shortcuts = List.copyOf(shortcuts == null ? List.of() : shortcuts);
    }
}
