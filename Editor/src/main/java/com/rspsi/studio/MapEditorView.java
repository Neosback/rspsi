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
import imgui.flag.ImGuiDir;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Objects;

/** Initial native Map Editor chrome; renderer and neutral panels attach here. */
public final class MapEditorView {
    private static final String DOCKSPACE_NAME = "MapEditorDockspace";
    private static final String TOOLS_WINDOW = "Tools";
    private static final String VIEWPORT_WINDOW = "Viewport";
    private static final String INSPECTOR_WINDOW = "Inspector";
    private static final String BOTTOM_WINDOW = "Assets / History / Validation";

    private String activeToolId;
    private boolean dockLayoutBuilt;

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
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Reset layout")) dockLayoutBuilt = false;
                ImGui.endMenu();
            }
            ImGui.sameLine();
            ImGui.text("Map Editor  ·  Revision " + cache.identity().revision());
            ImGui.endMainMenuBar();
        }

        renderDockHost();

        renderTools(pluginLifecycle);
        renderViewport(plan, viewport, sceneStatus,
                settings.snapshot().get(RenderSettingKeys.MSAA_SAMPLES));
        renderInspector(settings, pluginLifecycle);
        renderBottomPanel(pluginLifecycle);
    }

    /**
     * Hosts the dockspace covering the whole window below the menu bar and
     * builds the default {@code Tools | Viewport | Inspector} / bottom-drawer
     * split the first time it runs (or after View > Reset layout). Dear
     * ImGui's own docking then owns resize/move/detach/redock for every
     * frame after that - panels below no longer set their own position or
     * size. Layout persistence (ini) is intentionally disabled during
     * bootstrap (see ImGuiHost), so rebuilding the default split once per
     * launch cannot clobber a saved user layout; it only needs to avoid
     * rebuilding on every frame, which would fight live rearrangement.
     */
    private void renderDockHost() {
        imgui.ImGuiViewport viewport = ImGui.getMainViewport();
        float menuBarHeight = ImGui.getFrameHeight();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY() + menuBarHeight);
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY() - menuBarHeight);
        ImGui.setNextWindowViewport(viewport.getID());
        int hostFlags = ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoDocking;
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.begin("MapEditorDockHost", hostFlags);
        ImGui.popStyleVar(3);

        int dockspaceId = ImGui.getID(DOCKSPACE_NAME);
        if (!dockLayoutBuilt) {
            buildDefaultLayout(dockspaceId, viewport.getSizeX(), viewport.getSizeY() - menuBarHeight);
            dockLayoutBuilt = true;
        }
        ImGui.dockSpace(dockspaceId, 0.0f, 0.0f, ImGuiDockNodeFlags.PassthruCentralNode);
        ImGui.end();
    }

    private void buildDefaultLayout(int dockspaceId, float width, float height) {
        imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
        imgui.internal.ImGui.dockBuilderAddNode(dockspaceId, ImGuiDockNodeFlags.PassthruCentralNode);
        imgui.internal.ImGui.dockBuilderSetNodeSize(dockspaceId, width, height);

        ImInt toolsId = new ImInt();
        ImInt afterToolsId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(dockspaceId, ImGuiDir.Left, 0.14f, toolsId, afterToolsId);

        ImInt inspectorId = new ImInt();
        ImInt afterInspectorId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(afterToolsId.get(), ImGuiDir.Right, 0.24f,
                inspectorId, afterInspectorId);

        ImInt bottomId = new ImInt();
        ImInt viewportId = new ImInt();
        imgui.internal.ImGui.dockBuilderSplitNode(afterInspectorId.get(), ImGuiDir.Down, 0.26f,
                bottomId, viewportId);

        imgui.internal.ImGui.dockBuilderDockWindow(TOOLS_WINDOW, toolsId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(VIEWPORT_WINDOW, viewportId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(INSPECTOR_WINDOW, inspectorId.get());
        imgui.internal.ImGui.dockBuilderDockWindow(BOTTOM_WINDOW, bottomId.get());
        imgui.internal.ImGui.dockBuilderFinish(dockspaceId);
    }

    private void renderTools(EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.begin(TOOLS_WINDOW, ImGuiWindowFlags.NoCollapse);
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
                                       String sceneStatus, int msaaSamples) {
        ImGui.begin(VIEWPORT_WINDOW, ImGuiWindowFlags.NoCollapse);
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
            // Camera orbit/pan/zoom alone must never show geometryUploaded/
            // textureUploaded true two frames in a row, and drawCalls should
            // stay proportional to the plan's command count rather than
            // exploding near occluders - see OpenGlSceneRenderer.draw.
            ImGui.textDisabled((stats.geometryUploaded() ? "geometry upload · " : "")
                    + (stats.textureUploaded() ? "texture upload · " : "")
                    + stats.drawCalls() + " draw calls");
            viewport.render(plan, ImGui.getContentRegionAvailX(),
                    Math.max(160.0f, ImGui.getContentRegionAvailY()), msaaSamples);
        }
        ImGui.end();
    }

    private void renderInspector(SettingsStore settings,
                                 EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.begin(INSPECTOR_WINDOW, ImGuiWindowFlags.NoCollapse);
        ImGui.separatorText("Selection");
        ImGui.textDisabled("Nothing selected");
        ImGui.separatorText("Visibility");
        SettingsSnapshot snapshot = settings.snapshot();
        toggle(settings, RenderSettingKeys.TERRAIN_VISIBLE, "Terrain", snapshot);
        toggle(settings, RenderSettingKeys.OBJECTS_VISIBLE, "Objects", snapshot);
        toggle(settings, RenderSettingKeys.COLLISION_VISIBLE, "Collision", snapshot);
        toggle(settings, RenderSettingKeys.ROOFS_VISIBLE, "Roofs", snapshot);
        ImGui.separatorText("Renderer");
        msaaSlider(settings, snapshot);
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

    private static void msaaSlider(SettingsStore settings, SettingsSnapshot snapshot) {
        int[] value = {snapshot.get(RenderSettingKeys.MSAA_SAMPLES)};
        if (ImGui.sliderInt("MSAA samples", value, 0, 8)) {
            settings.set(RenderSettingKeys.MSAA_SAMPLES, value[0]);
        }
        ImGui.textDisabled("Clamped to the driver's GL_MAX_SAMPLES and rounded down to a power of two.");
    }

    private static void renderBottomPanel(EditorPluginLifecycleManager pluginLifecycle) {
        ImGui.begin(BOTTOM_WINDOW, ImGuiWindowFlags.NoCollapse);
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
