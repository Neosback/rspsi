package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.editor.integration.semantic.SemanticContentNodeKind;
import com.rspsi.editor.integration.semantic.SemanticFactKind;
import com.rspsi.project.ProjectIntegrationCapability;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.server.ServerPathKey;
import com.rspsi.server.ServerProjectInspection;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * In-project home.
 *
 * <p>Project/cache selection belongs to the pre-project launcher. This view presents project
 * health, OpenRune content intelligence, recent/continue actions, and workspace entry points.</p>
 */
public final class DashboardView {
    private static final NumberFormat NUMBER_FORMAT =
            NumberFormat.getIntegerInstance(Locale.US);

    private final ImString region = new ImString("50,50", 32);

    public void render(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            ServerIntegrationService integrations,
            Runnable openMapEditor,
            Runnable openInterfaceStudio,
            Runnable openObjectStudio,
            Runnable openIntegrationCenter,
            Runnable closeProject,
            WorkspaceManager workspaces,
            Runnable openDashboard,
            Consumer<WorkspaceManager.Workspace> closeWorkspace) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(openMapEditor, "openMapEditor");

        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("Project Home##openrune-studio", flags)) {
            ImGui.end();
            return;
        }

        if (workspaces != null) {
            StudioWidgets.workspaceTabs(
                    workspaces,
                    openDashboard,
                    openMapEditor,
                    openInterfaceStudio,
                    openObjectStudio,
                    closeWorkspace);
            ImGui.separator();
        }

        ImGui.dummy(1.0f, 12.0f);
        ImGui.indent(24.0f);

        renderProjectHeader(project, status, integrations, closeProject);
        ImGui.dummy(1.0f, 10.0f);

        renderContinue(project, status, openMapEditor);
        ImGui.dummy(1.0f, 10.0f);

        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
            renderOpenRuneContentHome(project, status, integrations, openIntegrationCenter);
            ImGui.dummy(1.0f, 10.0f);
        } else {
            renderStandaloneSummary(status);
            ImGui.dummy(1.0f, 10.0f);
        }

        renderWorkspaces(status, openMapEditor, openInterfaceStudio, openObjectStudio);
        ImGui.dummy(1.0f, 10.0f);
        renderDiagnostics(status);

        ImGui.dummy(1.0f, 22.0f);
        ImGui.unindent(24.0f);
        ImGui.end();
    }

    private static void renderProjectHeader(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            ServerIntegrationService integrations,
            Runnable closeProject) {
        StudioWidgets.beginCard("project-header", -1.0f, 112.0f);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.91f, 0.94f, 1.0f, 1.0f);
        ImGui.text(project.name());
        ImGui.popStyleColor();

        ImGui.sameLine(0.0f, 10.0f);
        boolean openRune = project.kind() == StudioProjectKind.OPENRUNE_SERVER;
        StudioWidgets.pill(
                openRune ? "OPENRUNE SERVER" : "STANDALONE CACHE",
                ImGui.getColorU32(
                        openRune ? 0.18f : 0.16f,
                        openRune ? 0.23f : 0.28f,
                        openRune ? 0.40f : 0.20f,
                        0.90f),
                ImGui.getColorU32(
                        openRune ? 0.58f : 0.43f,
                        openRune ? 0.70f : 0.90f,
                        openRune ? 1.00f : 0.55f,
                        1.00f));

        ImGui.sameLine();
        if (status.state() == CacheSessionState.READY) {
            StudioWidgets.pill(
                    "HEALTHY",
                    ImGui.getColorU32(0.13f, 0.28f, 0.18f, 0.85f),
                    ImGui.getColorU32(0.29f, 0.87f, 0.50f, 1.0f));
        }

        ImGui.textDisabled(project.sourcePathValue().toString());

        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);
        if (cache != null) {
            ImGui.text("Revision " + cache.identity().revision()
                    + "  ·  " + fmt(cache.mapCount()) + " map groups");
        } else {
            ImGui.textDisabled(status.message());
        }

        if (openRune && integrations != null) {
            integrations.activeProjectInspection().ifPresent(inspection -> {
                String branch = inspection.git().available() && !inspection.git().branch().isBlank()
                        ? inspection.git().branch() : "Git unavailable";
                String suffix = inspection.git().dirty() ? " · modified" : "";
                ImGui.sameLine(0.0f, 14.0f);
                ImGui.textDisabled(branch + suffix);
            });
        }

        if (closeProject != null) {
            float buttonWidth = 110.0f;
            ImGui.sameLine(Math.max(ImGui.getCursorPosX() + 12.0f,
                    ImGui.getWindowContentRegionMaxX() - buttonWidth));
            if (StudioWidgets.buttonGhost("Close Project", buttonWidth, 26.0f)) {
                closeProject.run();
            }
        }

        StudioWidgets.endCard();
    }

    private void renderContinue(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            Runnable openMapEditor) {
        boolean ready = status.state() == CacheSessionState.READY;
        StudioWidgets.beginCard("project-continue", -1.0f, 112.0f);
        ImGui.text(StudioIcons.MAP + "  Continue");
        ImGui.textDisabled(ready
                ? "Open Map Studio at a region. Workspace scene loading starts only after you enter it."
                : "Project services are not ready.");

        ImGui.dummy(1.0f, 5.0f);
        ImGui.setNextItemWidth(220.0f);
        ImGui.inputTextWithHint("##home-region", "Region X,Y or ID", region);
        ImGui.sameLine();

        ImGui.beginDisabled(!ready);
        if (StudioWidgets.buttonPrimary("Open Map Studio", 150.0f, 30.0f)) {
            openMapEditor.run();
        }
        ImGui.endDisabled();

        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
            ImGui.sameLine();
            ImGui.textDisabled("Content semantics stay connected while you edit the world.");
        }
        StudioWidgets.endCard();
    }

    private static void renderOpenRuneContentHome(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            ServerIntegrationService integrations,
            Runnable openIntegrationCenter) {
        ImGui.separatorText("OpenRune Content Home");

        ServerProjectInspection inspection = integrations == null
                ? null : integrations.activeProjectInspection().orElse(null);
        var source = integrations == null
                ? null : integrations.activeSemanticSourceIndex().orElse(null);
        var graph = integrations == null
                ? null : integrations.activeSemanticContentGraph().orElse(null);

        int modules = inspection == null ? 0
                : inspection.gradleModel().map(model -> model.projects().size()).orElse(0);
        int buildTasks = inspection == null ? 0 : inspection.buildTasks().size();
        int scripts = source == null ? 0
                : source.facts(SemanticFactKind.PLUGIN_SCRIPT).size();
        int handlers = source == null ? 0
                : source.facts(SemanticFactKind.SCRIPT_HANDLER).size();
        int quests = graph == null ? 0
                : graph.nodes(SemanticContentNodeKind.QUEST).size();
        int objects = graph == null ? 0
                : graph.nodes(SemanticContentNodeKind.OBJECT_DEFINITION).size();
        int graphNodes = graph == null ? 0 : graph.nodes().size();
        int graphEdges = graph == null ? 0 : graph.edges().size();
        int symbols = integrations == null ? 0 : integrations.symbolService().totalSymbolCount();
        int references = integrations == null ? 0
                : integrations.referenceService().totalReferenceCount();
        int spawns = integrations == null ? 0
                : integrations.npcSpawnService().totalSpawnCount();

        if (ImGui.beginTable("##openrune-content-metrics", 4,
                ImGuiTableFlags.SizingStretchSame)) {
            metricCell("MODULES", fmt(modules), "Gradle projects");
            metricCell("SCRIPTS", fmt(scripts), fmt(handlers) + " handlers");
            metricCell("QUESTS", fmt(quests), "semantic graph");
            metricCell("OBJECT CONTENT", fmt(objects), "authored overlays");
            metricCell("GAMEVALS / SYMBOLS", fmt(symbols), "RSCM + authored mappings");
            metricCell("REFERENCES", fmt(references), "declarative source links");
            metricCell("NPC SPAWNS", fmt(spawns), "server spawn data");
            metricCell("CONTENT GRAPH", fmt(graphNodes), fmt(graphEdges) + " relationships");
            ImGui.endTable();
        }

        ImGui.dummy(1.0f, 8.0f);
        if (ImGui.beginTable("##openrune-project-health", 2,
                ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp)) {
            statusRow("LIVE cache", pathLabel(inspection, ServerPathKey.LIVE_CACHE,
                    status.state() == CacheSessionState.READY ? "Ready" : "Unavailable"));
            statusRow("SERVER cache", pathLabel(inspection, ServerPathKey.SERVER_CACHE, "Not detected"));
            statusRow("Source semantics", source == null
                    ? "Unavailable" : fmt(source.files().size()) + " Kotlin files indexed");
            statusRow("Content graph", graph == null
                    ? "Unavailable" : fmt(graphNodes) + " nodes / " + fmt(graphEdges) + " edges");
            statusRow("Build tooling", buildTasks == 0
                    ? "No supported task discovered" : buildTasks + " supported project task(s)");
            statusRow("Integration policy", project.capabilities().contains(
                    ProjectIntegrationCapability.CACHE_BUILD)
                    ? "Managed build enabled" : project.capabilities().contains(
                    ProjectIntegrationCapability.PROJECT_SOURCE_WRITE)
                    ? "Author" : "Inspect");
            ImGui.endTable();
        }

        if (inspection != null && !inspection.diagnostics().isEmpty()) {
            ImGui.dummy(1.0f, 6.0f);
            ImGui.textDisabled(inspection.diagnostics().size()
                    + " integration diagnostic(s) available.");
        }

        ImGui.dummy(1.0f, 8.0f);
        if (openIntegrationCenter != null
                && StudioWidgets.buttonSecondary("OpenRune Integration & Build", 210.0f, 30.0f)) {
            openIntegrationCenter.run();
        }
        ImGui.sameLine();
        ImGui.textDisabled(
                "Project setup stays in Project Settings/Integration—not on the Content Home.");
    }

    private static void renderStandaloneSummary(CacheSessionStatus status) {
        ImGui.separatorText("Project Status");
        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);
        StudioWidgets.beginCard("standalone-project-health", -1.0f, 94.0f);
        if (cache == null) {
            ImGui.textDisabled(status.message());
        } else {
            ImGui.textColored(
                    StudioDrawColors.abgr(0xFF4ADE80),
                    StudioIcons.CHECK + "  Cache ready");
            ImGui.text("Revision " + cache.identity().revision()
                    + "  ·  " + fmt(cache.mapCount()) + " map groups");
            ImGui.textDisabled(cache.path().toString());
        }
        StudioWidgets.endCard();
    }

    private static void renderWorkspaces(
            CacheSessionStatus status,
            Runnable openMapEditor,
            Runnable openInterfaceStudio,
            Runnable openObjectStudio) {
        ImGui.separatorText("Workspaces");
        boolean ready = status.state() == CacheSessionState.READY;
        float spacing = 12.0f;
        float width = Math.max(230.0f,
                (ImGui.getContentRegionAvailX() - spacing * 2.0f) / 3.0f);

        workspaceCard(
                "home-map",
                StudioIcons.MAP,
                "Map Studio",
                "World editing with server-content semantics attached to selected objects.",
                ready,
                openMapEditor,
                width);
        ImGui.sameLine(0.0f, spacing);
        workspaceCard(
                "home-interface",
                StudioIcons.PREFAB,
                "Interface Studio",
                "Interfaces, components, sprites and future CS2 relationships.",
                ready,
                openInterfaceStudio,
                width);
        ImGui.sameLine(0.0f, spacing);
        workspaceCard(
                "home-object",
                StudioIcons.OBJECT,
                "Object Studio",
                "Definitions, models, animations and server object overlays.",
                ready,
                openObjectStudio,
                width);
    }

    private static void workspaceCard(
            String id,
            String icon,
            String title,
            String description,
            boolean ready,
            Runnable action,
            float width) {
        StudioWidgets.beginCard(id, width, 144.0f);
        ImGui.text(icon + "  " + title);
        ImGui.textWrapped(description);
        ImGui.dummy(1.0f, 8.0f);
        ImGui.beginDisabled(!ready || action == null);
        if (StudioWidgets.buttonSecondary("Open " + title + "##" + id, -1.0f, 30.0f)
                && action != null) {
            action.run();
        }
        ImGui.endDisabled();
        StudioWidgets.endCard();
    }

    private static void renderDiagnostics(CacheSessionStatus status) {
        if (status.state() != CacheSessionState.READY) return;
        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);
        if (cache == null || cache.decoderSummary() == null) return;

        if (!ImGui.collapsingHeader("Cache Diagnostics")) return;

        var summary = cache.decoderSummary();
        if (summary.allDecodersPassed()) {
            ImGui.textColored(
                    StudioDrawColors.abgr(0xFF4ADE80),
                    StudioIcons.CHECK + "  Required decoders healthy");
        } else {
            ImGui.text("Decoder diagnostics: " + summary.failures().size());
        }
        ImGui.textDisabled(
                fmt(summary.totalDefinitionsCount()) + " definitions  ·  "
                        + fmt(summary.totalAudioCount()) + " audio assets  ·  "
                        + fmt(summary.totalArchives()) + " archives across "
                        + summary.totalIndices() + " indices");
        ImGui.textDisabled(
                "Detailed cache census belongs in Project Diagnostics, not the project home.");
    }

    private static void metricCell(String title, String value, String detail) {
        ImGui.tableNextColumn();
        StudioWidgets.beginCard("metric-" + title, -1.0f, 82.0f);
        ImGui.textDisabled(title);
        ImGui.text(value);
        ImGui.textDisabled(detail);
        StudioWidgets.endCard();
    }

    private static void statusRow(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.text(value);
    }

    private static String pathLabel(
            ServerProjectInspection inspection,
            ServerPathKey key,
            String fallback) {
        if (inspection == null) return fallback;
        return inspection.path(key).map(path -> path.toString()).orElse(fallback);
    }

    private static String fmt(int value) {
        return NUMBER_FORMAT.format(value);
    }

    public String regionText() {
        return region.get().trim();
    }
}
