package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.integration.ServerIntegrationService;
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

        renderProjectHeader(project, status, closeProject);
        ImGui.dummy(1.0f, 10.0f);

        renderContinue(project, status, openMapEditor);
        ImGui.dummy(1.0f, 10.0f);

        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
            renderOpenRuneProjectStatus(project, status, integrations, openIntegrationCenter);
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

    private static void renderOpenRuneProjectStatus(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            ServerIntegrationService integrations,
            Runnable openIntegrationCenter) {
        ImGui.separatorText("OpenRune Project");

        ServerProjectInspection inspection = integrations == null
                ? null : integrations.activeProjectInspection().orElse(null);
        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);

        StudioWidgets.beginCard("openrune-project-status", -1.0f, 190.0f);
        if (ImGui.beginTable("##openrune-project-health", 2,
                ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp)) {
            statusRow("LIVE cache", cache != null
                    ? "Ready · revision " + cache.identity().revision()
                    : pathLabel(inspection, ServerPathKey.LIVE_CACHE, "Unavailable"));
            statusRow("SERVER cache",
                    pathState(inspection, ServerPathKey.SERVER_CACHE, "Not detected"));
            statusRow("Access", accessLabel(project));
            statusRow("Build access",
                    project.capabilities().contains(ProjectIntegrationCapability.CACHE_BUILD)
                            ? "Allowed when explicitly requested"
                            : "Not granted");
            ImGui.endTable();
        }

        ImGui.dummy(1.0f, 8.0f);
        ImGui.textDisabled(
                "Studio opens the LIVE cache first. Source/content indexing is deferred until "
                        + "a future content workspace explicitly needs it.");
        ImGui.dummy(1.0f, 8.0f);

        if (openIntegrationCenter != null
                && StudioWidgets.buttonSecondary("Project integration", 170.0f, 30.0f)) {
            openIntegrationCenter.run();
        }
        StudioWidgets.endCard();
    }

    private static String accessLabel(StudioProjectDescriptor project) {
        boolean write = project.capabilities().contains(
                ProjectIntegrationCapability.PROJECT_SOURCE_WRITE);
        boolean build = project.capabilities().contains(
                ProjectIntegrationCapability.CACHE_BUILD);
        boolean launch = project.capabilities().contains(
                ProjectIntegrationCapability.SERVER_LAUNCH);
        if (launch) return "Read + write + build + server launch";
        if (build) return "Read + write + build";
        if (write) return "Read + write";
        return "Read only";
    }

    private static String pathState(
            ServerProjectInspection inspection,
            ServerPathKey key,
            String fallback) {
        if (inspection == null) return fallback;
        return inspection.path(key)
                .map(path -> java.nio.file.Files.isDirectory(path)
                        ? "Ready · " + path
                        : "Missing · " + path)
                .orElse(fallback);
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
