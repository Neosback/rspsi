package com.rspsi.studio;

import com.rspsi.project.ProjectIntegrationPreset;
import com.rspsi.project.RecentStudioProject;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.project.StudioProjectKind;
import com.rspsi.project.StudioProjectRegistry;
import com.rspsi.project.StudioProjectService;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioWidgets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Lightweight launcher shown before any cache or server project is opened.
 *
 * <p>The startup contract is intentionally small: pick a recent project, import
 * an OpenRune-Server checkout, or choose a cache directly. Studio owns the
 * project name/data directory automatically so startup never turns into a
 * configuration form.</p>
 */
public final class ProjectLauncherView {
    private static final DateTimeFormatter RECENT_TIME =
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");
    private static final float PANEL_MAX_WIDTH = 740.0f;
    private static final float PANEL_MAX_HEIGHT = 700.0f;

    private final StudioProjectRegistry registry;
    private final StudioProjectService projects;

    private Path pendingOpenRuneRoot;
    private ProjectIntegrationPreset preset = ProjectIntegrationPreset.MANAGED_BUILD;
    private String error = "";

    public ProjectLauncherView(StudioProjectRegistry registry, StudioProjectService projects) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.projects = Objects.requireNonNull(projects, "projects");
    }

    public void render(Consumer<StudioProjectDescriptor> openProject) {
        Objects.requireNonNull(openProject, "openProject");

        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY());
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoDocking;
        if (!ImGui.begin("OpenRune Studio Projects", flags)) {
            ImGui.end();
            return;
        }

        float panelWidth = Math.min(PANEL_MAX_WIDTH,
                Math.max(520.0f, ImGui.getContentRegionAvailX() - 80.0f));
        float panelHeight = Math.min(PANEL_MAX_HEIGHT,
                Math.max(560.0f, ImGui.getContentRegionAvailY() - 60.0f));
        float left = Math.max(24.0f,
                (ImGui.getContentRegionAvailX() - panelWidth) * 0.5f);
        float top = Math.max(20.0f,
                (ImGui.getContentRegionAvailY() - panelHeight) * 0.5f);

        ImGui.setCursorPosX(ImGui.getCursorPosX() + left);
        ImGui.setCursorPosY(ImGui.getCursorPosY() + top);
        ImGui.beginChild("##project-launcher-shell", panelWidth, panelHeight, false);

        renderHeader();
        ImGui.dummy(1.0f, 20.0f);

        ImGui.pushFont(StudioFonts.heading(), 23.0f);
        ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.97f, 1.0f, 1.0f);
        ImGui.textUnformatted("Projects");
        ImGui.popStyleColor();
        ImGui.popFont();
        ImGui.textDisabled("Open a recent project or choose how Studio should start.");
        ImGui.dummy(1.0f, 12.0f);

        renderRecent(openProject);
        ImGui.dummy(1.0f, 12.0f);
        renderStartActions(openProject);

        if (pendingOpenRuneRoot != null) {
            ImGui.dummy(1.0f, 12.0f);
            renderAccessSelection(openProject);
        }

        if (!error.isBlank()) {
            ImGui.dummy(1.0f, 10.0f);
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.48f, 0.48f, 1.0f);
            ImGui.textWrapped(error);
            ImGui.popStyleColor();
        }

        ImGui.endChild();
        ImGui.end();
    }

    private void renderHeader() {
        float logoWidth = Math.min(300.0f, ImGui.getContentRegionAvailX() * 0.44f);
        float x = Math.max(0.0f,
                (ImGui.getContentRegionAvailX() - logoWidth) * 0.5f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + x);
        StudioBranding.drawWordmark(logoWidth);
    }

    private void renderRecent(Consumer<StudioProjectDescriptor> openProject) {
        List<RecentStudioProject> recent = registry.recent();
        float height = pendingOpenRuneRoot == null ? 310.0f : 205.0f;

        ImGui.pushStyleColor(ImGuiCol.ChildBg,
                ImGui.getColorU32(0.075f, 0.085f, 0.11f, 0.70f));
        ImGui.beginChild("##recent-project-list", -1.0f, height, true);

        if (recent.isEmpty()) {
            float y = Math.max(20.0f, height * 0.38f);
            ImGui.dummy(1.0f, y);
            centeredMuted("No recent projects yet");
            ImGui.endChild();
            ImGui.popStyleColor();
            return;
        }

        int row = 0;
        for (RecentStudioProject project : recent) {
            renderRecentProject(project, row++, openProject);
            if (row < recent.size()) ImGui.separator();
        }

        ImGui.endChild();
        ImGui.popStyleColor();
    }

    private void renderRecentProject(
            RecentStudioProject project,
            int row,
            Consumer<StudioProjectDescriptor> openProject) {
        ImGui.pushID("recent-" + row + "-" + project.projectId());
        ImGui.dummy(1.0f, 5.0f);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.96f, 1.0f, 1.0f);
        ImGui.textUnformatted(project.name());
        ImGui.popStyleColor();

        ImGui.sameLine(0.0f, 10.0f);
        String kind = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                ? "OpenRune-Server" : "Cache";
        int pillBg = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                ? ImGui.getColorU32(0.00f, 0.55f, 0.55f, 0.20f)
                : ImGui.getColorU32(0.18f, 0.23f, 0.32f, 0.85f);
        int pillText = project.kind() == StudioProjectKind.OPENRUNE_SERVER
                ? ImGui.getColorU32(0.18f, 0.92f, 0.88f, 1.0f)
                : ImGui.getColorU32(0.72f, 0.78f, 0.88f, 1.0f);
        StudioWidgets.pill(kind, pillBg, pillText);

        ImGui.textDisabled(project.displayPath());
        ImGui.textDisabled("Last opened "
                + RECENT_TIME.format(Instant.ofEpochMilli(project.lastOpenedEpochMillis())
                .atZone(ZoneId.systemDefault())));

        if (!project.available()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.67f, 0.25f, 1.0f);
            ImGui.textUnformatted("Project file is missing or has moved.");
            ImGui.popStyleColor();
        }

        ImGui.beginDisabled(!project.available());
        if (StudioWidgets.buttonPrimary("Open", 92.0f, 30.0f)) {
            tryOpen(project.descriptor(), openProject);
        }
        ImGui.endDisabled();

        if (project.kind() == StudioProjectKind.OPENRUNE_SERVER && project.available()) {
            ImGui.sameLine();
            if (StudioWidgets.buttonSecondary("Relocate", 92.0f, 30.0f)) {
                relocateOpenRune(project, openProject);
            }
        }

        ImGui.sameLine();
        if (StudioWidgets.buttonGhost("Remove", 82.0f, 30.0f)) {
            registry.remove(project.projectId());
        }

        ImGui.dummy(1.0f, 5.0f);
        ImGui.popID();
    }

    private void renderStartActions(Consumer<StudioProjectDescriptor> openProject) {
        if (StudioWidgets.buttonPrimary("Import OpenRune-Server", -1.0f, 44.0f)) {
            chooseOpenRuneRoot();
        }
        ImGui.dummy(1.0f, 8.0f);
        if (StudioWidgets.buttonSecondary("Continue without import", -1.0f, 44.0f)) {
            chooseStandaloneCache(openProject);
        }
    }

    private void chooseOpenRuneRoot() {
        NativeFileDialogs.chooseDirectory(
                "Choose OpenRune-Server directory",
                Path.of(System.getProperty("user.home")))
                .ifPresent(path -> {
                    pendingOpenRuneRoot = path;
                    error = "";
                });
    }

    private void chooseStandaloneCache(Consumer<StudioProjectDescriptor> openProject) {
        NativeFileDialogs.chooseDirectory(
                "Choose OSRS cache directory",
                Path.of(System.getProperty("user.home")))
                .ifPresent(path -> {
                    try {
                        StudioProjectDescriptor descriptor = projects.createStandalone(path);
                        pendingOpenRuneRoot = null;
                        error = "";
                        openProject.accept(descriptor);
                    } catch (Exception failure) {
                        error = rootMessage(failure);
                    }
                });
    }

    private void renderAccessSelection(Consumer<StudioProjectDescriptor> openProject) {
        StudioWidgets.beginCard("openrune-import-access", -1.0f, 0.0f);

        ImGui.pushStyleColor(ImGuiCol.Text, 0.94f, 0.97f, 1.0f, 1.0f);
        ImGui.textUnformatted("OpenRune-Server access");
        ImGui.popStyleColor();
        ImGui.textDisabled(pendingOpenRuneRoot.toString());
        ImGui.dummy(1.0f, 8.0f);
        ImGui.textWrapped(
                "Choose exactly how much access Studio gets. These labels describe permissions, "
                        + "not user roles.");

        ImGui.dummy(1.0f, 8.0f);
        for (ProjectIntegrationPreset candidate : ProjectIntegrationPreset.values()) {
            boolean selected = candidate == preset;
            if (selected) {
                if (StudioWidgets.buttonPrimary(
                        accessLabel(candidate) + "##access-" + candidate.name(),
                        -1.0f, 34.0f)) {
                    preset = candidate;
                }
            } else if (StudioWidgets.buttonSecondary(
                    accessLabel(candidate) + "##access-" + candidate.name(),
                    -1.0f, 34.0f)) {
                preset = candidate;
            }
            ImGui.dummy(1.0f, 4.0f);
        }

        ImGui.pushStyleColor(ImGuiCol.Text, 0.76f, 0.82f, 0.90f, 1.0f);
        ImGui.textWrapped(accessDescription(preset));
        ImGui.popStyleColor();
        ImGui.dummy(1.0f, 5.0f);
        ImGui.textDisabled(accessCapabilities(preset));
        ImGui.textDisabled("Fresh Cache/reset access is never granted automatically.");

        ImGui.dummy(1.0f, 10.0f);
        if (StudioWidgets.buttonPrimary("Import project", 150.0f, 34.0f)) {
            try {
                StudioProjectDescriptor descriptor =
                        projects.linkOpenRune(pendingOpenRuneRoot, preset);
                pendingOpenRuneRoot = null;
                error = "";
                openProject.accept(descriptor);
            } catch (Exception failure) {
                error = rootMessage(failure);
            }
        }
        ImGui.sameLine();
        if (StudioWidgets.buttonGhost("Cancel", 86.0f, 34.0f)) {
            pendingOpenRuneRoot = null;
            error = "";
        }

        StudioWidgets.endCard();
    }

    private void tryOpen(
            Path descriptor,
            Consumer<StudioProjectDescriptor> openProject) {
        try {
            StudioProjectDescriptor project = projects.open(descriptor);
            error = "";
            openProject.accept(project);
        } catch (Exception failure) {
            error = rootMessage(failure);
        }
    }

    private void relocateOpenRune(
            RecentStudioProject recent,
            Consumer<StudioProjectDescriptor> openProject) {
        NativeFileDialogs.chooseDirectory(
                "Relocate OpenRune-Server directory",
                Path.of(System.getProperty("user.home")))
                .ifPresent(path -> {
                    try {
                        StudioProjectDescriptor updated =
                                projects.relocateOpenRune(recent.descriptor(), path);
                        error = "";
                        openProject.accept(updated);
                    } catch (Exception failure) {
                        error = rootMessage(failure);
                    }
                });
    }

    private static String accessLabel(ProjectIntegrationPreset preset) {
        return switch (preset) {
            case INSPECT -> "Read only";
            case AUTHOR -> "Read + write";
            case MANAGED_BUILD -> "Read + write + build";
            case DEVELOPER -> "Development access";
        };
    }

    private static String accessCapabilities(ProjectIntegrationPreset preset) {
        return switch (preset) {
            case INSPECT -> "Access: project read";
            case AUTHOR -> "Access: project read · supported source write";
            case MANAGED_BUILD ->
                    "Access: project read · supported source write · declared cache/GameVal/CS2 builds";
            case DEVELOPER ->
                    "Access: read/write · declared builds · server launch · adapter-declared external commands";
        };
    }

    private static String accessDescription(ProjectIntegrationPreset preset) {
        return switch (preset) {
            case INSPECT ->
                    "Can read the project, cache paths and supported project metadata. No source writes or build commands.";
            case AUTHOR ->
                    "Read access plus supported source/content writes. Build and launch commands stay disabled.";
            case MANAGED_BUILD ->
                    "Read and write access plus Studio-recognized cache, GameVal and CS2 build tasks. It does not grant arbitrary external commands or server launch.";
            case DEVELOPER ->
                    "Adds server launch and adapter-declared external commands for development workflows. Fresh Cache/reset and other destructive replacement operations still require separate explicit authorization.";
        };
    }

    private static void centeredMuted(String text) {
        float width = ImGui.calcTextSize(text).x;
        ImGui.setCursorPosX(ImGui.getCursorPosX()
                + Math.max(0.0f, (ImGui.getContentRegionAvailX() - width) * 0.5f));
        ImGui.textDisabled(text);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }
}
