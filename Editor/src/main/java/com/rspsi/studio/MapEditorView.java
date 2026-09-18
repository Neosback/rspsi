package com.rspsi.studio;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.render.GpuUploadPlan;
import com.rspsi.editor.render.RenderTextureResource;
import com.rspsi.editor.render.RenderSettingKeys;
import com.rspsi.editor.plugin.EditorPluginLifecycleManager;
import com.rspsi.editor.plugin.EditorNotificationService;
import com.rspsi.editor.plugin.EditorSetting;
import com.rspsi.editor.plugin.EditorToolRegistration;
import com.rspsi.editor.plugin.EditorTaskService;
import com.rspsi.editor.settings.SettingKey;
import com.rspsi.editor.settings.SettingsSnapshot;
import com.rspsi.editor.settings.SettingsStore;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Objects;

/** Initial native Map Editor chrome; renderer and neutral panels attach here. */
public final class MapEditorView {
    private String activeToolId;

    public void render(LoadedOsrsCacheSession cache, GpuUploadPlan plan,
                       NativeSceneViewport viewport, String sceneStatus,
                       Runnable openDashboard, SettingsStore settings,
                       EditorPluginLifecycleManager pluginLifecycle) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(openDashboard, "dashboard callback");

        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Dashboard")) openDashboard.run();
                ImGui.endMenu();
            }
            ImGui.sameLine();
            ImGui.text("Map Editor  ·  Revision " + cache.identity().revision());
            ImGui.endMainMenuBar();
        }

        float width = ImGui.getIO().getDisplaySizeX();
        float height = ImGui.getIO().getDisplaySizeY();
        float contentTop = 28.0f;
        float bottomHeight = 130.0f;
        float sideWidth = 220.0f;
        float inspectorWidth = 300.0f;
        float viewportWidth = Math.max(320.0f, width - sideWidth - inspectorWidth - 24.0f);
        float viewportHeight = Math.max(200.0f, height - contentTop - bottomHeight - 16.0f);

        renderTools(pluginLifecycle, 8.0f, contentTop, sideWidth, viewportHeight);
        renderViewport(plan, viewport, sceneStatus, sideWidth + 12.0f, contentTop,
                viewportWidth, viewportHeight,
                settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES));
        renderInspector(settings, pluginLifecycle, width - inspectorWidth - 8.0f,
                contentTop, inspectorWidth, viewportHeight);
        renderBottomPanel(pluginLifecycle, 8.0f, height - bottomHeight - 8.0f,
                width - inspectorWidth - 16.0f, bottomHeight);
    }

    private static void windowLayout(float x, float y, float width, float height) {
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(width, height);
    }

    private void renderTools(EditorPluginLifecycleManager pluginLifecycle,
                             float x, float y, float width, float height) {
        windowLayout(x, y, width, height);
        ImGui.begin("Tools", ImGuiWindowFlags.NoCollapse);
        if (pluginLifecycle == null || pluginLifecycle.host() == null) {
            ImGui.textDisabled("Loading editor tools...");
        } else {
            String category = null;
            for (EditorToolRegistration tool : pluginLifecycle.host().registry().toolRegistrations()) {
                if (!tool.category().equals(category)) {
                    category = tool.category();
                    ImGui.separatorText(category);
                }
                if (ImGui.button(tool.label())) activeToolId = tool.id();
            }
        }
        ImGui.end();
    }

    private static void renderViewport(GpuUploadPlan plan, NativeSceneViewport viewport,
                                       String sceneStatus, float x, float y,
                                       float width, float height, int msaaSamples) {
        windowLayout(x, y, width, height);
        ImGui.begin("Viewport", ImGuiWindowFlags.NoCollapse);
        if (plan == null) {
            ImGui.text(sceneStatus == null ? "Preparing scene..." : sceneStatus);
            ImGui.progressBar(0.35f, -1, 0, "Scene data");
        } else {
            long decodedTextures = plan.textures().values().stream()
                    .filter(RenderTextureResource::hasPixels).count();
            ImGui.textDisabled("GPU plan: " + plan.vertices().size() + " vertices · "
                    + plan.indices().size() + " indices · " + plan.commands().size() + " draws");
            ImGui.textDisabled("Textures: " + decodedTextures + "/" + plan.textures().size()
                    + " decoded · occluders: " + plan.occluders().size());
            var stats = viewport.statistics();
            ImGui.textDisabled("Native: " + stats.terrainTriangles() + " terrain · "
                    + stats.objectTriangles() + " objects · "
                    + stats.renderedTriangles() + " submitted · textures "
                    + stats.decodedTextures() + "/" + stats.fallbackTextures() + "/"
                    + stats.unavailableTextures() + " · GL error " + stats.firstGlError());
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()), msaaSamples);
        }
        ImGui.end();
    }

    private void renderInspector(SettingsStore settings,
                                 EditorPluginLifecycleManager pluginLifecycle,
                                 float x, float y, float width, float height) {
        windowLayout(x, y, width, height);
        ImGui.begin("Inspector", ImGuiWindowFlags.NoCollapse);
        ImGui.separatorText("Selection");
        ImGui.textDisabled("Nothing selected");
        ImGui.separatorText("Visibility");
        SettingsSnapshot snapshot = settings.snapshot();
        toggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain", snapshot);
        toggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Objects", snapshot);
        toggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision", snapshot);
        toggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs", snapshot);
        ImGui.separatorText("Renderer");
        ImGui.textDisabled("MSAA unavailable until the FBO acceptance gate passes.");
        if (pluginLifecycle != null && pluginLifecycle.host() != null && activeToolId != null) {
            var host = pluginLifecycle.host();
            var toolSettings = host.registry().settingsForTool(host.context(), activeToolId);
            if (!toolSettings.isEmpty()) {
                ImGui.separatorText("Tool · " + activeToolId);
                for (EditorSetting setting : toolSettings) renderToolSetting(setting);
            }
        }
        ImGui.end();
    }

    private static void renderToolSetting(EditorSetting setting) {
        switch (setting.type()) {
            case INTEGER -> {
                int value = ((Number) setting.value()).intValue();
                int[] values = {value};
                int minimum = (int) setting.minimum();
                int maximum = (int) setting.maximum();
                boolean changed;
                if (maximum - (long) minimum <= 1_000_000L) {
                    changed = ImGui.sliderInt(setting.label(), values, minimum, maximum);
                } else {
                    ImInt input = new ImInt(value);
                    changed = ImGui.inputInt(setting.label(), input);
                    values[0] = input.get();
                }
                if (changed) setting.setValue(values[0]);
            }
            case DECIMAL -> {
                float[] value = {((Number) setting.value()).floatValue()};
                if (ImGui.sliderFloat(setting.label(), value,
                        (float) setting.minimum(), (float) setting.maximum())) {
                    setting.setValue((double) value[0]);
                }
            }
            case BOOLEAN -> {
                ImBoolean value = new ImBoolean((Boolean) setting.value());
                if (ImGui.checkbox(setting.label(), value)) setting.setValue(value.get());
            }
            case ENUM -> {
                String current = String.valueOf(setting.value());
                String[] options = setting.options().toArray(String[]::new);
                ImInt selected = new ImInt(Math.max(0, setting.options().indexOf(current)));
                if (ImGui.combo(setting.label(), selected, options)) {
                    setting.setValue(options[selected.get()]);
                }
            }
        }
    }

    private static void toggle(SettingsStore settings, SettingKey<Boolean> key,
                                String label, SettingsSnapshot snapshot) {
        ImBoolean value = new ImBoolean(snapshot.get(key));
        if (ImGui.checkbox(label, value)) settings.set(key, value.get());
    }

    private static void renderBottomPanel(EditorPluginLifecycleManager pluginLifecycle,
                                          float x, float y, float width, float height) {
        windowLayout(x, y, width, height);
        ImGui.begin("Assets / History / Validation", ImGuiWindowFlags.NoCollapse);
        if (ImGui.beginTabBar("editor-bottom-tabs")) {
            if (ImGui.beginTabItem("Assets")) {
                ImGui.textDisabled("Asset browser will consume the neutral AssetRepository.");
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("History")) {
                ImGui.textDisabled("Undo history is owned by EditorSession.");
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Validation")) {
                ImGui.textDisabled("WorldValidator diagnostics will appear here.");
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Plugins")) {
                int count = pluginLifecycle == null || pluginLifecycle.host() == null
                        ? 0 : pluginLifecycle.host().plugins().size();
                ImGui.text("Active plugins: " + count);
                if (pluginLifecycle != null) {
                    for (var plugin : pluginLifecycle.candidates()) {
                        ImGui.bulletText(plugin.id() + " · "
                                + pluginLifecycle.status(plugin.id()).name());
                    }
                }
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Tasks")) {
                if (pluginLifecycle == null || pluginLifecycle.host() == null) {
                    ImGui.textDisabled("No active workspace tasks.");
                } else {
                    for (EditorTaskService.TaskSnapshot task
                            : pluginLifecycle.host().context().tasks().snapshots()) {
                        ImGui.text(task.label() + " · " + task.state());
                        ImGui.progressBar((float) task.progress(), -1, 0, task.message());
                    }
                }
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Messages")) {
                if (pluginLifecycle == null || pluginLifecycle.host() == null) {
                    ImGui.textDisabled("No workspace notifications.");
                } else {
                    for (EditorNotificationService.Notification notification
                            : pluginLifecycle.host().context().notifications().recent()) {
                        ImGui.text(notification.level() + " · " + notification.title());
                        ImGui.textWrapped(notification.message());
                    }
                }
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }
}
