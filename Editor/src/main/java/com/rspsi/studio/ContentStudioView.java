package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import com.rspsi.cache.workspace.CacheSessionStatus;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.OsrsCacheHealth;
import com.rspsi.editor.integration.ServerIntegrationService;
import com.rspsi.project.ProjectIntegrationCapability;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.server.ServerPathKey;
import com.rspsi.server.ServerProjectInspection;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.theme.StudioPalette;
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
 * OpenRune Content Studio project home.
 *
 * <p>Project/cache selection belongs to the pre-project launcher. This surface is the durable
 * project workspace for launching interconnected authoring and analysis tools. A lightweight
 * FileStore health check is enough to enter Content Studio; individual workspaces request the
 * heavier cache definitions and content indexes they actually need.</p>
 */
public final class ContentStudioView {
    private static final NumberFormat NUMBER_FORMAT =
            NumberFormat.getIntegerInstance(Locale.US);

    private final ImString region = new ImString("50,50", 32);

    public void render(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth,
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
                | ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoScrollbar
                | ImGuiWindowFlags.NoScrollWithMouse;
        if (!ImGui.begin("OpenRune Content Studio##openrune-studio", flags)) {
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

        float outerWidth = ImGui.getContentRegionAvailX();
        float bodyWidth = Math.min(1120.0f, Math.max(1.0f, outerWidth - 32.0f));
        float bodyOffset = Math.max(0.0f, (outerWidth - bodyWidth) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + bodyOffset);
        ImGui.beginChild("##content-studio-body", bodyWidth, ImGui.getContentRegionAvailY(), false);
        ImGui.setScrollX(0.0f);
        ImGui.pushTextWrapPos(0.0f);
        ImGui.dummy(1.0f, 12.0f);

        renderProjectHeader(project, status, cacheHealth, closeProject);
        ImGui.dummy(1.0f, 10.0f);

        renderContinue(project, status, cacheHealth, openMapEditor);
        ImGui.dummy(1.0f, 10.0f);

        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER) {
            renderOpenRuneProjectStatus(project, status, cacheHealth, integrations, openIntegrationCenter);
            ImGui.dummy(1.0f, 10.0f);
        } else {
            renderStandaloneSummary(status, cacheHealth);
            ImGui.dummy(1.0f, 10.0f);
        }

        renderWorkspaces(status, cacheHealth, openMapEditor, openInterfaceStudio, openObjectStudio);
        ImGui.dummy(1.0f, 10.0f);
        renderDiagnostics(status);

        ImGui.dummy(1.0f, 18.0f);
        ImGui.popTextWrapPos();
        ImGui.endChild();
        ImGui.end();
    }

    private static void renderProjectHeader(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth,
            Runnable closeProject) {
        StudioWidgets.beginCard("project-header", -1.0f, 136.0f);

        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
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
        if (cacheHealth != null) {
            StudioWidgets.pill(
                    "HEALTHY",
                    ImGui.getColorU32(0.13f, 0.28f, 0.18f, 0.85f),
                    ImGui.getColorU32(0.29f, 0.87f, 0.50f, 1.0f));
        }

        ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT_DISABLED);
        ImGui.textWrapped(project.sourcePathValue().toString());
        ImGui.popStyleColor();

        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);
        if (cache != null) {
            ImGui.text("Workspace cache loaded  ·  Revision " + cache.identity().revision()
                    + "  ·  " + fmt(cache.mapCount()) + " map groups");
        } else if (cacheHealth != null) {
            ImGui.text("FileStore ready  ·  Revision " + cacheHealth.revision()
                    + "  ·  " + fmt(cacheHealth.mapArchiveCount()) + " map groups");
        } else {
            ImGui.textDisabled(status.message());
        }

        if (closeProject != null) {
            float buttonWidth = 110.0f;
            float offset = ImGui.getContentRegionAvailX() - buttonWidth;
            if (offset > 0.0f) ImGui.setCursorPosX(ImGui.getCursorPosX() + offset);
            if (StudioWidgets.buttonGhost("Close Project", buttonWidth, 26.0f)) {
                closeProject.run();
            }
        }

        StudioWidgets.endCard();
    }

    private void renderContinue(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth,
            Runnable openMapEditor) {
        boolean loading = status.state() == CacheSessionState.LOADING;
        boolean ready = cacheHealth != null && !loading;
        StudioWidgets.beginCard("project-continue", -1.0f, 132.0f);
        ImGui.text(StudioIcons.MAP + "  Continue");
        ImGui.textDisabled(loading
                ? "Preparing cache definitions for the requested workspace..."
                : ready
                ? "Open Map Studio at a region. Definitions and scene data load only when you enter it."
                : "Project FileStore is not ready.");

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
            ImGui.dummy(1.0f, 4.0f);
            ImGui.textDisabled("The imported OpenRune project stays connected while you edit.");
        }
        StudioWidgets.endCard();
    }

    private static void renderOpenRuneProjectStatus(
            StudioProjectDescriptor project,
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth,
            ServerIntegrationService integrations,
            Runnable openIntegrationCenter) {
        ImGui.separatorText("OpenRune Project");

        ServerProjectInspection inspection = integrations == null
                ? null : integrations.activeProjectInspection().orElse(null);
        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);

        StudioWidgets.beginCard("openrune-project-status", -1.0f, 226.0f);
        if (ImGui.beginTable("##openrune-project-health", 2,
                ImGuiTableFlags.RowBg | ImGuiTableFlags.SizingStretchProp)) {
            statusRow("LIVE cache", cache != null
                    ? "Workspace loaded · revision " + cache.identity().revision()
                    : cacheHealth != null
                    ? "FileStore healthy · revision " + cacheHealth.revision()
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
                "Content Studio starts from a lightweight FileStore health check. Definitions, "
                        + "RSCM/GameVals, source indexing, spawns and semantic graphs activate "
                        + "only when a tool requests them.");
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
        boolean commands = project.capabilities().contains(
                ProjectIntegrationCapability.EXTERNAL_COMMAND);
        if (launch || commands) {
            return "Development access · read/write/build/launch/declared commands";
        }
        if (build) return "Managed build · read/write/declared builds";
        if (write) return "Read + write · supported source";
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

    private static void renderStandaloneSummary(
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth) {
        ImGui.separatorText("Project Status");
        LoadedOsrsCacheSession cache = status.currentSession().orElse(null);
        StudioWidgets.beginCard("standalone-project-health", -1.0f, 94.0f);
        if (cache == null && cacheHealth == null) {
            ImGui.textDisabled(status.message());
        } else if (cache == null) {
            ImGui.textColored(
                    StudioPalette.SUCCESS,
                    StudioIcons.CHECK + "  FileStore ready");
            ImGui.text("Revision " + cacheHealth.revision()
                    + "  ·  " + fmt(cacheHealth.mapArchiveCount()) + " map groups");
            ImGui.textDisabled(cacheHealth.backendName() + "  ·  " + cacheHealth.path());
        } else {
            ImGui.textColored(
                    StudioPalette.SUCCESS,
                    StudioIcons.CHECK + "  Cache ready");
            ImGui.text("Revision " + cache.identity().revision()
                    + "  ·  " + fmt(cache.mapCount()) + " map groups");
            ImGui.textDisabled(cache.path().toString());
        }
        StudioWidgets.endCard();
    }

    private static void renderWorkspaces(
            CacheSessionStatus status,
            OsrsCacheHealth cacheHealth,
            Runnable openMapEditor,
            Runnable openInterfaceStudio,
            Runnable openObjectStudio) {
        ImGui.separatorText("Workspaces");
        boolean ready = cacheHealth != null && status.state() != CacheSessionState.LOADING;
        float spacing = 12.0f;
        float available = ImGui.getContentRegionAvailX();
        int columns = available >= 820.0f ? 3 : available >= 520.0f ? 2 : 1;
        float width = Math.max(1.0f, (available - spacing * (columns - 1)) / columns);

        workspaceCard(
                "home-map",
                StudioIcons.MAP,
                "Map Studio",
                "World editing, terrain, objects, selection and project-aware cache loading.",
                ready,
                openMapEditor,
                width);
        if (columns > 1) ImGui.sameLine(0.0f, spacing);
        workspaceCard(
                "home-interface",
                StudioIcons.PREFAB,
                "Interface Studio",
                "Interfaces, components and sprites.",
                ready,
                openInterfaceStudio,
                width);
        if (columns == 3) ImGui.sameLine(0.0f, spacing);
        else if (columns == 2) ImGui.dummy(1.0f, spacing);
        workspaceCard(
                "home-object",
                StudioIcons.OBJECT,
                "Object Studio",
                "Definitions, models, animations and asset inspection.",
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
                    StudioPalette.SUCCESS,
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

    private static void statusRow(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.textWrapped(value);
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
